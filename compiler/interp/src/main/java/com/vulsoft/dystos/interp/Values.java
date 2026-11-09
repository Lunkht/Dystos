package com.vulsoft.dystos.interp;

import com.vulsoft.dystos.ast.Ast;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Valeurs d'exécution. Entiers : Long ; flottants : Double ; texte : String ; bool : Boolean ; null : null. */
final class Values {

    private Values() {
    }

    static final Object NOT_FOUND = new Object();

    interface Callable {
        Object call(List<Object> args, Map<String, Object> named);
    }

    /** Classe Dystos (ou exception prédéfinie). */
    static final class ClassDef {
        final String name;
        ClassDef superclass;
        final List<ClassDef> interfaces = new ArrayList<>();
        boolean isInterface;
        boolean isAbstract;
        boolean isEnum;
        boolean builtin;
        final Map<String, FuncValue> methods = new LinkedHashMap<>();
        final Map<String, FuncValue> statics = new LinkedHashMap<>();
        FuncValue ctor;
        final List<Ast.VarDecl> fields = new ArrayList<>();
        final Set<String> floatFields = new java.util.HashSet<>();
        final Map<String, DObject> enumConstants = new LinkedHashMap<>();
        Env env;

        ClassDef(String name) {
            this.name = name;
        }

        boolean isSubclassOf(ClassDef other) {
            if (this == other) {
                return true;
            }
            if (superclass != null && superclass.isSubclassOf(other)) {
                return true;
            }
            for (ClassDef i : interfaces) {
                if (i.isSubclassOf(other)) {
                    return true;
                }
            }
            return false;
        }

        FuncValue findMethod(String m) {
            for (ClassDef c = this; c != null; c = c.superclass) {
                FuncValue f = c.methods.get(m);
                if (f != null) {
                    return f;
                }
                for (ClassDef i : c.interfaces) {
                    FuncValue g = i.findMethod(m);
                    if (g != null) {
                        return g;
                    }
                }
            }
            return null;
        }

        FuncValue findCtor() {
            for (ClassDef c = this; c != null; c = c.superclass) {
                if (c.ctor != null) {
                    return c.ctor;
                }
            }
            return null;
        }

        boolean isFloatField(String f) {
            for (ClassDef c = this; c != null; c = c.superclass) {
                if (c.floatFields.contains(f)) {
                    return true;
                }
            }
            return false;
        }
    }

    static final class DObject {
        final ClassDef cls;
        final Map<String, Object> fields = new LinkedHashMap<>();

        DObject(ClassDef cls) {
            this.cls = cls;
        }
    }

    /** Fonction ou méthode Dystos. */
    static final class FuncValue {
        final Ast.FuncDecl decl;
        final Env closure;
        final ClassDef owner;

        FuncValue(Ast.FuncDecl decl, Env closure, ClassDef owner) {
            this.decl = decl;
            this.closure = closure;
            this.owner = owner;
        }
    }

    static final class LambdaValue {
        final Ast.Lambda lambda;
        final Env env;

        LambdaValue(Ast.Lambda lambda, Env env) {
            this.lambda = lambda;
            this.env = env;
        }
    }

    record BoundMethod(FuncValue func, DObject self) {
    }

    /** Méthode native liée à un récepteur (str, list, map, objet Java...). */
    record BoundNative(Object receiver, String name) {
    }

    /** Fonction ou type prédéfini (print, len, str, int...). */
    record BuiltinFn(String name) {
    }

    record EntryValue(Object key, Object value) {
    }

    static final class ModuleValue {
        final String name;
        final Env env;
        final Map<String, Object> natives;

        ModuleValue(String name, Env env, Map<String, Object> natives) {
            this.name = name;
            this.env = env;
            this.natives = natives;
        }
    }

    record JavaClassRef(Class<?> cls) {
    }

    record JavaStaticMethod(Class<?> cls, String name) {
    }

    record NativeFn(String name, Callable fn) {
    }

    static final class TaskValue {
        Object result;
        RuntimeException error;
    }

    // ------------------------------------------------------------------
    // Formatage
    // ------------------------------------------------------------------

    /** Représentation d'un flottant à la manière de Python (13.0, 0.1, 1e+16). */
    static String formatDouble(double d) {
        if (Double.isNaN(d)) {
            return "nan";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? "inf" : "-inf";
        }
        if (d == 0) {
            return (1 / d < 0) ? "-0.0" : "0.0";
        }
        double a = Math.abs(d);
        String s = Double.toString(d);
        if (a >= 1e-4 && a < 1e16) {
            if (s.contains("E")) {
                s = new java.math.BigDecimal(s).stripTrailingZeros().toPlainString();
                if (!s.contains(".")) {
                    s += ".0";
                }
            }
            return s;
        }
        int e = s.indexOf('E');
        String mant = e < 0 ? s : s.substring(0, e);
        int exp = e < 0 ? 0 : Integer.parseInt(s.substring(e + 1));
        if (mant.endsWith(".0")) {
            mant = mant.substring(0, mant.length() - 2);
        }
        String sign = exp < 0 ? "-" : "+";
        int ae = Math.abs(exp);
        return mant + "e" + sign + (ae < 10 ? "0" + ae : String.valueOf(ae));
    }

    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\t': sb.append("\\t"); break;
                case '\r': sb.append("\\r"); break;
                default: sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
