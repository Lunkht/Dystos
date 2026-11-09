package com.vulsoft.dystos.jvm;

import static com.vulsoft.dystos.jvm.classfile.Code.*;

import com.vulsoft.dystos.jvm.classfile.Code;
import com.vulsoft.dystos.sema.Type;
import com.vulsoft.dystos.sema.Type.FunctionType;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;

/**
 * Appels de code Java : descripteurs exacts (tirés de la réflexion) et conversions aux frontières.
 *
 * <p>Côté Dystos : {@code int}=long, {@code float}=double, {@code str}=String (même pour un {@code char}).
 * Côté Java, les paramètres et résultats peuvent être {@code int}, {@code short}, {@code byte}, {@code float},
 * {@code char}, leurs classes d'encadrement, ou {@code Object} (génériques). Ce qui n'a pas de conversion sûre
 * (tableaux, nombre variable d'arguments, interfaces fonctionnelles) est refusé avec {@code E0401}.
 */
final class JavaCall {

    private JavaCall() {
    }

    static String typeDesc(Class<?> c) {
        String d = MethodType.methodType(c).toMethodDescriptorString();
        return d.substring(d.indexOf(')') + 1);
    }

    static String methodDesc(Executable e) {
        Class<?> ret = e instanceof Method m ? m.getReturnType() : void.class;
        return MethodType.methodType(ret, e.getParameterTypes()).toMethodDescriptorString();
    }

    static String internal(Class<?> c) {
        return c.getName().replace('.', '/');
    }

    private static UnsupportedException no(String message) {
        return new UnsupportedException(message);
    }

    private static void need(boolean ok, Type from, Class<?> to) {
        if (!ok) {
            throw no("conversion de " + from + " vers le type Java " + to.getSimpleName() + " non supportée");
        }
    }

    /** Convertit la valeur au sommet de la pile (type Dystos {@code from}) vers le paramètre Java {@code to}. */
    static void toJava(MethodGen mg, Type from, Class<?> to) {
        Code code = mg.code;
        if (from instanceof FunctionType) {
            throw no("passer une fonction Dystos à du code Java (interface fonctionnelle) n'est pas encore supporté");
        }
        if (to.isArray()) {
            throw no("les tableaux Java ne sont pas encore supportés");
        }
        char kf = Jt.kind(from);
        if (to.isPrimitive()) {
            if (to == long.class) {
                need(kf == 'J', from, to);
            } else if (to == int.class) {
                need(kf == 'J', from, to);
                code.invokestatic(Jt.RT, "toJavaInt", "(J)I");
            } else if (to == short.class) {
                need(kf == 'J', from, to);
                code.invokestatic(Jt.RT, "toJavaShort", "(J)S");
            } else if (to == byte.class) {
                need(kf == 'J', from, to);
                code.invokestatic(Jt.RT, "toJavaByte", "(J)B");
            } else if (to == double.class) {
                if (kf == 'J') {
                    code.simple(L2D, 0);
                } else {
                    need(kf == 'D', from, to);
                }
            } else if (to == float.class) {
                if (kf == 'J') {
                    code.simple(L2D, 0);
                } else {
                    need(kf == 'D', from, to);
                }
                code.simple(D2F, -1);
            } else if (to == boolean.class) {
                need(kf == 'I', from, to);
            } else if (to == char.class) {
                need(Jt.isStr(from) && !from.nullable(), from, to);
                code.invokestatic(Jt.RT, "toJavaChar", "(Ljava/lang/String;)C");
            } else {
                throw no("type Java primitif non supporté : " + to.getSimpleName());
            }
            return;
        }
        if (kf != 'A') {                                   // primitif Dystos -> référence Java
            if (to == Integer.class) {
                need(kf == 'J', from, to);
                code.invokestatic(Jt.RT, "toJavaInt", "(J)I");
                code.invokestatic("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
            } else if (to == Float.class) {
                if (kf == 'J') {
                    code.simple(L2D, 0);
                } else {
                    need(kf == 'D', from, to);
                }
                code.simple(D2F, -1);
                code.invokestatic("java/lang/Float", "valueOf", "(F)Ljava/lang/Float;");
            } else {
                Class<?> boxed = kf == 'J' ? Long.class : kf == 'D' ? Double.class : Boolean.class;
                need(to.isAssignableFrom(boxed), from, to);
                mg.box(kf);
            }
            return;
        }
        if (Jt.isObjectLike(from) && to != Object.class) {
            code.checkcast(internal(to));
        }
    }

    /** Convertit le résultat Java (classe {@code r}) vers la valeur Dystos de type {@code natural}. */
    static void fromJava(MethodGen mg, Class<?> r, Type natural) {
        Code code = mg.code;
        if (r == void.class || r == long.class || r == double.class || r == boolean.class) {
            return;
        }
        if (r == int.class || r == short.class || r == byte.class) {
            code.simple(I2L, 1);
            return;
        }
        if (r == float.class) {
            code.simple(F2D, 1);
            return;
        }
        if (r == char.class) {
            code.invokestatic("java/lang/String", "valueOf", "(C)Ljava/lang/String;");
            return;
        }
        if (r.isArray()) {
            throw no("les tableaux Java ne sont pas encore supportés");
        }
        char ks = Jt.kind(natural);
        if (ks == 'J') {
            code.invokestatic(Jt.RT, "javaToLong", "(Ljava/lang/Object;)J");
        } else if (ks == 'D') {
            code.invokestatic(Jt.RT, "javaToDouble", "(Ljava/lang/Object;)D");
        } else if (ks == 'I') {
            code.invokestatic(Jt.RT, "javaToBool", "(Ljava/lang/Object;)Z");
        } else if (ks == 'V') {
            code.pop('A');
        } else if (r == Character.class) {
            code.invokestatic(Jt.RT, "javaCharToString", "(Ljava/lang/Object;)Ljava/lang/String;");
        } else if (r == Object.class) {
            mg.coerce(MethodGen.ERASED, natural);
        }
    }

    static boolean isJava(Constructor<?> c) {
        return c != null;
    }
}
