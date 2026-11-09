package com.vulsoft.dystos.jvm.classfile;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Écrivain de fichiers {@code .class} (format version 49, sans {@code StackMapTable} : la JVM vérifie ces classes
 * par inférence de types). Pas d'{@code invokedynamic} ni de méthodes par défaut d'interface (versions ≥ 51/52).
 */
public final class ClassWriter {

    public static final int ACC_PUBLIC = 0x0001;
    public static final int ACC_PRIVATE = 0x0002;
    public static final int ACC_STATIC = 0x0008;
    public static final int ACC_FINAL = 0x0010;
    public static final int ACC_SUPER = 0x0020;
    public static final int ACC_INTERFACE = 0x0200;
    public static final int ACC_ABSTRACT = 0x0400;

    private static final class Field {
        final int access;
        final String name;
        final String desc;

        Field(int access, String name, String desc) {
            this.access = access;
            this.name = name;
            this.desc = desc;
        }
    }

    /** Méthode en cours d'écriture. */
    public static final class Method {
        final int access;
        final String name;
        final String desc;
        private final Code code;

        Method(int access, String name, String desc, Code code) {
            this.access = access;
            this.name = name;
            this.desc = desc;
            this.code = code;
        }

        /** Corps de la méthode ({@code null} pour une méthode abstraite). */
        public Code code() {
            return code;
        }
    }

    private final ConstPool cp = new ConstPool();
    private final int access;
    private final String name;
    private final String superName;
    private final List<String> interfaces;
    private final List<Field> fields = new ArrayList<>();
    private final List<Method> methods = new ArrayList<>();

    public ClassWriter(int access, String name, String superName, List<String> interfaces) {
        this.access = access;
        this.name = name;
        this.superName = superName;
        this.interfaces = List.copyOf(interfaces);
    }

    public String name() {
        return name;
    }

    public void field(int access, String name, String desc) {
        fields.add(new Field(access, name, desc));
    }

    public Method method(int access, String name, String desc) {
        boolean isStatic = (access & ACC_STATIC) != 0;
        Code code = (access & ACC_ABSTRACT) != 0 ? null : new Code(cp, (isStatic ? 0 : 1) + Code.argSlots(desc));
        Method m = new Method(access, name, desc, code);
        methods.add(m);
        return m;
    }

    public byte[] toBytes() {
        try {
            // On force l'entrée des constantes de structure avant l'écriture du reste.
            int thisIdx = cp.cls(name);
            int superIdx = cp.cls(superName);
            List<Integer> ifaceIdx = new ArrayList<>();
            for (String i : interfaces) {
                ifaceIdx.add(cp.cls(i));
            }
            int codeAttr = cp.utf8("Code");
            List<int[]> fieldIdx = new ArrayList<>();
            for (Field f : fields) {
                fieldIdx.add(new int[] {f.access, cp.utf8(f.name), cp.utf8(f.desc)});
            }
            List<byte[]> codes = new ArrayList<>();
            List<int[]> methodIdx = new ArrayList<>();
            for (Method m : methods) {
                methodIdx.add(new int[] {m.access, cp.utf8(m.name), cp.utf8(m.desc)});
                codes.add(m.code == null ? null : m.code.toAttribute());
            }

            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(bo);
            o.writeInt(0xCAFEBABE);
            o.writeShort(0);
            o.writeShort(49);
            o.writeShort(cp.count());
            o.write(cp.toBytes());
            o.writeShort(access);
            o.writeShort(thisIdx);
            o.writeShort(superIdx);
            o.writeShort(ifaceIdx.size());
            for (int i : ifaceIdx) {
                o.writeShort(i);
            }
            o.writeShort(fieldIdx.size());
            for (int[] f : fieldIdx) {
                o.writeShort(f[0]);
                o.writeShort(f[1]);
                o.writeShort(f[2]);
                o.writeShort(0);
            }
            o.writeShort(methodIdx.size());
            for (int i = 0; i < methodIdx.size(); i++) {
                int[] m = methodIdx.get(i);
                o.writeShort(m[0]);
                o.writeShort(m[1]);
                o.writeShort(m[2]);
                byte[] code = codes.get(i);
                if (code == null) {
                    o.writeShort(0);
                } else {
                    o.writeShort(1);
                    o.writeShort(codeAttr);
                    o.writeInt(code.length);
                    o.write(code);
                }
            }
            o.writeShort(0);
            return bo.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
