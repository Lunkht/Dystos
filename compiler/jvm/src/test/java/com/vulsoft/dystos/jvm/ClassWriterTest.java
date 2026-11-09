package com.vulsoft.dystos.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vulsoft.dystos.jvm.classfile.ClassWriter;
import com.vulsoft.dystos.jvm.classfile.Code;
import com.vulsoft.dystos.jvm.classfile.Label;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassWriterTest {

    private static final class Loader extends ClassLoader {
        Class<?> define(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    private static Class<?> load(ClassWriter cw) {
        return new Loader().define(cw.name().replace('/', '.'), cw.toBytes());
    }

    private static ClassWriter newClass(String name) {
        return new ClassWriter(ClassWriter.ACC_PUBLIC | ClassWriter.ACC_SUPER, name, "java/lang/Object", List.of());
    }

    @Test
    void loopWithLongsAndLabels() throws Exception {
        ClassWriter cw = newClass("Gen1");
        Code c = cw.method(ClassWriter.ACC_PUBLIC | ClassWriter.ACC_STATIC, "sum", "(J)J").code();
        int s = c.newLocal('J');
        int i = c.newLocal('J');
        c.lconst(0);
        c.store('J', s);
        c.lconst(0);
        c.store('J', i);
        Label top = new Label();
        Label end = new Label();
        c.label(top);
        c.load('J', i);
        c.load('J', 0);
        c.simple(Code.LCMP, -3);
        c.jump(Code.IFGE, end);
        c.load('J', s);
        c.load('J', i);
        c.simple(Code.LADD, -2);
        c.store('J', s);
        c.load('J', i);
        c.lconst(1);
        c.simple(Code.LADD, -2);
        c.store('J', i);
        c.jump(Code.GOTO, top);
        c.label(end);
        c.load('J', s);
        c.ret('J');
        assertEquals(4950L, load(cw).getMethod("sum", long.class).invoke(null, 100L));
        assertEquals(0L, load(cw).getMethod("sum", long.class).invoke(null, 0L));
    }

    @Test
    void doublesStringsAndStaticCalls() throws Exception {
        ClassWriter cw = newClass("Gen2");
        Code c = cw.method(ClassWriter.ACC_PUBLIC | ClassWriter.ACC_STATIC, "f", "(D)Ljava/lang/String;").code();
        c.anew("java/lang/StringBuilder");
        c.simple(Code.DUP, 1);
        c.invokespecial("java/lang/StringBuilder", "<init>", "()V");
        c.ldcString("x=");
        c.invokevirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        c.load('D', 0);
        c.dconst(0.5);
        c.simple(Code.DADD, -2);
        c.invokevirtual("java/lang/StringBuilder", "append", "(D)Ljava/lang/StringBuilder;");
        c.invokevirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
        c.ret('A');
        assertEquals("x=2.0", load(cw).getMethod("f", double.class).invoke(null, 1.5));
    }

    @Test
    void exceptionTable() throws Exception {
        ClassWriter cw = newClass("Gen3");
        Code c = cw.method(ClassWriter.ACC_PUBLIC | ClassWriter.ACC_STATIC, "f", "(I)J").code();
        Label start = new Label();
        Label end = new Label();
        Label handler = new Label();
        c.label(start);
        c.anew("java/lang/IllegalStateException");
        c.simple(Code.DUP, 1);
        c.invokespecial("java/lang/IllegalStateException", "<init>", "()V");
        c.simple(Code.ATHROW, -1);
        c.label(end);
        c.handlerLabel(handler);
        c.simple(Code.POP, -1);
        c.lconst(7);
        c.ret('J');
        c.tryCatch(start, end, handler, "java/lang/IllegalStateException");
        assertEquals(7L, load(cw).getMethod("f", int.class).invoke(null, 0));
    }

    @Test
    void deadCodeAfterReturnIsIgnored() throws Exception {
        ClassWriter cw = newClass("Gen4");
        Code c = cw.method(ClassWriter.ACC_PUBLIC | ClassWriter.ACC_STATIC, "f", "()J").code();
        c.lconst(5);
        c.ret('J');
        c.lconst(9);           // code mort : ignoré
        c.simple(Code.POP2, -2);
        c.ret('J');
        assertEquals(5L, load(cw).getMethod("f").invoke(null));
    }

    @Test
    void fieldsConstructorsAndVirtualCalls() throws Exception {
        ClassWriter cw = newClass("Gen5");
        cw.field(ClassWriter.ACC_PUBLIC, "n", "J");
        Code init = cw.method(ClassWriter.ACC_PUBLIC, "<init>", "(J)V").code();
        init.load('A', 0);
        init.invokespecial("java/lang/Object", "<init>", "()V");
        init.load('A', 0);
        init.load('J', 1);
        init.putfield("Gen5", "n", "J");
        init.ret('V');
        Code get = cw.method(ClassWriter.ACC_PUBLIC, "twice", "()J").code();
        get.load('A', 0);
        get.getfield("Gen5", "n", "J");
        get.lconst(2);
        get.simple(Code.LADD, -2);
        get.ret('J');
        Class<?> k = load(cw);
        Object o = k.getConstructor(long.class).newInstance(40L);
        assertEquals(42L, k.getMethod("twice").invoke(o));
    }

    @Test
    void invalidStackIsRejectedAtWriteTime() {
        ClassWriter cw = newClass("Gen6");
        Code c = cw.method(ClassWriter.ACC_PUBLIC | ClassWriter.ACC_STATIC, "f", "()V").code();
        assertThrows(IllegalStateException.class, () -> c.simple(Code.POP, -1));
    }

    @Test
    void theJvmVerifierRejectsBrokenBytecode() {
        ClassWriter cw = newClass("Gen7");
        Code c = cw.method(ClassWriter.ACC_PUBLIC | ClassWriter.ACC_STATIC, "f", "()J").code();
        c.iconst(1);          // retourne un int là où un long est attendu
        c.simple(Code.IRETURN, -1);
        Class<?> k = load(cw);
        assertThrows(VerifyError.class, () -> {
            try {
                k.getMethod("f").invoke(null);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
}
