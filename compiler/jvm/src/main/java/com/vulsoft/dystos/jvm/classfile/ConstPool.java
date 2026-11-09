package com.vulsoft.dystos.jvm.classfile;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Table des constantes d'un fichier de classe (entrées dédoublonnées). */
final class ConstPool {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final DataOutputStream out = new DataOutputStream(bytes);
    private final Map<String, Integer> index = new HashMap<>();
    private int count = 1; // l'indice 0 est réservé

    int utf8(String s) {
        Integer i = index.get("U" + s);
        if (i != null) {
            return i;
        }
        try {
            out.writeByte(1);
            out.writeUTF(s);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return remember("U" + s, 1);
    }

    int cls(String internalName) {
        Integer i = index.get("C" + internalName);
        if (i != null) {
            return i;
        }
        int n = utf8(internalName);
        write(7, n);
        return remember("C" + internalName, 1);
    }

    int string(String s) {
        Integer i = index.get("S" + s);
        if (i != null) {
            return i;
        }
        int n = utf8(s);
        write(8, n);
        return remember("S" + s, 1);
    }

    int integer(int v) {
        Integer i = index.get("I" + v);
        if (i != null) {
            return i;
        }
        try {
            out.writeByte(3);
            out.writeInt(v);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return remember("I" + v, 1);
    }

    int longConst(long v) {
        Integer i = index.get("J" + v);
        if (i != null) {
            return i;
        }
        try {
            out.writeByte(5);
            out.writeLong(v);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return remember("J" + v, 2);
    }

    int doubleConst(double v) {
        long bits = Double.doubleToRawLongBits(v);
        Integer i = index.get("D" + bits);
        if (i != null) {
            return i;
        }
        try {
            out.writeByte(6);
            out.writeLong(bits);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return remember("D" + bits, 2);
    }

    int nameAndType(String name, String desc) {
        String key = "N" + name + ":" + desc;
        Integer i = index.get(key);
        if (i != null) {
            return i;
        }
        int n = utf8(name);
        int d = utf8(desc);
        write(12, n, d);
        return remember(key, 1);
    }

    int field(String owner, String name, String desc) {
        return ref(9, "F", owner, name, desc);
    }

    int method(String owner, String name, String desc) {
        return ref(10, "M", owner, name, desc);
    }

    int interfaceMethod(String owner, String name, String desc) {
        return ref(11, "IM", owner, name, desc);
    }

    private int ref(int tag, String prefix, String owner, String name, String desc) {
        String key = prefix + owner + "." + name + ":" + desc;
        Integer i = index.get(key);
        if (i != null) {
            return i;
        }
        int c = cls(owner);
        int nt = nameAndType(name, desc);
        write(tag, c, nt);
        return remember(key, 1);
    }

    private void write(int tag, int a) {
        try {
            out.writeByte(tag);
            out.writeShort(a);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void write(int tag, int a, int b) {
        try {
            out.writeByte(tag);
            out.writeShort(a);
            out.writeShort(b);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private int remember(String key, int slots) {
        int i = count;
        index.put(key, i);
        count += slots;
        return i;
    }

    int count() {
        return count;
    }

    byte[] toBytes() {
        return bytes.toByteArray();
    }
}
