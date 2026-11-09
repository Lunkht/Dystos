package com.vulsoft.dystos.jvm;

import com.vulsoft.dystos.sema.Symbols.ClassKind;
import com.vulsoft.dystos.sema.Symbols.ClassSymbol;
import com.vulsoft.dystos.sema.Type;
import com.vulsoft.dystos.sema.Type.ClassType;
import com.vulsoft.dystos.sema.Type.ErrorType;
import com.vulsoft.dystos.sema.Type.FunctionType;
import com.vulsoft.dystos.sema.Type.NullType;
import com.vulsoft.dystos.sema.Type.TypeVar;
import com.vulsoft.dystos.sema.Type.UnitType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Correspondance entre les types de Dystos et ceux de la JVM.
 *
 * <p>{@code int}=long (J), {@code float}=double (D), {@code bool}=boolean (Z), {@code str}=String,
 * {@code list/map/set}=interfaces {@code java.util}, génériques par effacement (Object). Un type primitif
 * nullable ({@code int?}) devient sa classe d'encadrement (Long, Double, Boolean).
 * Types de valeurs ({@code kind}) : 'I' (bool), 'J', 'D', 'A' (référence), 'V' (aucune valeur).
 */
final class Jt {

    static final String OBJECT = "java/lang/Object";
    static final String STRING = "java/lang/String";
    static final String LONG = "java/lang/Long";
    static final String DOUBLE = "java/lang/Double";
    static final String BOOLEAN = "java/lang/Boolean";
    static final String LIST = "java/util/List";
    static final String MAP = "java/util/Map";
    static final String SET = "java/util/Set";
    static final String RT = "dystos/rt/Rt";
    static final String ENTRY = "dystos/rt/Entry";
    static final String DOBJECT = "dystos/rt/DObject";
    static final String DENUM = "dystos/rt/DEnum";
    static final String DEXCEPTION = "dystos/rt/DException";
    static final String FN = "dystos/rt/Fn";
    static final String REF = "dystos/rt/Ref";

    private Jt() {
    }

    // ------------------------------------------------------------------
    // Genre de valeur
    // ------------------------------------------------------------------

    static char kind(Type t) {
        if (t instanceof UnitType) {
            return 'V';
        }
        if (t instanceof ClassType ct && !ct.nullable() && ct.cls().kind == ClassKind.BUILTIN) {
            switch (ct.cls().name) {
                case "int": return 'J';
                case "float": return 'D';
                case "bool": return 'I';
                default: return 'A';
            }
        }
        if (t instanceof ErrorType) {
            throw new IllegalStateException("type erroné dans le programme à compiler");
        }
        return 'A';
    }

    /** Vrai pour les types dont la représentation JVM est {@code Object} (génériques effacés, {@code any}, null). */
    static boolean isObjectLike(Type t) {
        if (t instanceof TypeVar || t instanceof NullType) {
            return true;
        }
        return t instanceof ClassType ct && ct.cls().kind == ClassKind.BUILTIN && ct.cls().name.equals("any");
    }

    static boolean isBuiltin(Type t, String name) {
        return t instanceof ClassType ct && ct.cls().kind == ClassKind.BUILTIN && ct.cls().name.equals(name);
    }

    static boolean isInt(Type t) {
        return isBuiltin(t, "int") && !t.nullable();
    }

    static boolean isFloat(Type t) {
        return isBuiltin(t, "float") && !t.nullable();
    }

    static boolean isBool(Type t) {
        return isBuiltin(t, "bool") && !t.nullable();
    }

    static boolean isStr(Type t) {
        return isBuiltin(t, "str");
    }

    // ------------------------------------------------------------------
    // Descripteurs et noms
    // ------------------------------------------------------------------

    static String desc(Type t) {
        if (t instanceof UnitType) {
            return "V";
        }
        if (t instanceof NullType || t instanceof TypeVar) {
            return "L" + OBJECT + ";";
        }
        if (t instanceof FunctionType) {
            return "L" + FN + ";";
        }
        if (t instanceof ErrorType) {
            throw new IllegalStateException("type erroné dans le programme à compiler");
        }
        ClassType ct = (ClassType) t;
        ClassSymbol cs = ct.cls();
        if (cs.kind == ClassKind.BUILTIN) {
            switch (cs.name) {
                case "int": return ct.nullable() ? "L" + LONG + ";" : "J";
                case "float": return ct.nullable() ? "L" + DOUBLE + ";" : "D";
                case "bool": return ct.nullable() ? "L" + BOOLEAN + ";" : "Z";
                default: return "L" + internalName(cs) + ";";
            }
        }
        return "L" + internalName(cs) + ";";
    }

    /** Nom interne de la classe JVM d'un type référence (pour {@code checkcast}, {@code new}...). */
    static String internal(Type t) {
        String d = desc(t);
        if (d.length() == 1) {
            throw new IllegalStateException("pas de classe pour " + t);
        }
        return d.substring(1, d.length() - 1);
    }

    static String internalName(ClassSymbol cs) {
        switch (cs.kind) {
            case BUILTIN:
                switch (cs.name) {
                    case "str": return STRING;
                    case "list": return LIST;
                    case "map": return MAP;
                    case "set": return SET;
                    case "Entry": return ENTRY;
                    case "File": return "dystos/rt/DFile";
                    case "Task": return "dystos/rt/DTask";
                    case "any": return OBJECT;
                    case "int": return LONG;
                    case "float": return DOUBLE;
                    case "bool": return BOOLEAN;
                    case "Exception": return DEXCEPTION;
                    case "ValueError": return "dystos/rt/DValueError";
                    case "RuntimeError": return "dystos/rt/DRuntimeError";
                    default:
                        throw new UnsupportedException("le type prédéfini '" + cs.name + "' n'est pas encore supporté par le backend JVM");
                }
            case JAVA:
                return cs.qualifiedName.replace('.', '/');
            default:
                return modulePath(cs.module.name) + "/" + cs.name;
        }
    }

    static boolean isInterface(ClassSymbol cs) {
        return cs.kind == ClassKind.INTERFACE || cs.javaInterface;
    }

    static String modulePath(String module) {
        return module.replace('.', '/');
    }

    static String moduleClass(String module) {
        return modulePath(module) + "/$Module";
    }

    static String methodDesc(List<Type> params, Type ret) {
        StringBuilder sb = new StringBuilder("(");
        for (Type p : params) {
            sb.append(desc(p));
        }
        return sb.append(')').append(desc(ret)).toString();
    }

    // ------------------------------------------------------------------
    // Substitution de paramètres de type
    // ------------------------------------------------------------------

    static Type subst(Type t, Map<TypeVar, Type> m) {
        if (m.isEmpty()) {
            return t;
        }
        if (t instanceof TypeVar tv) {
            Type r = m.get(tv.key());
            if (r == null) {
                return t;
            }
            return tv.nullable() ? r.withNullable(true) : r;
        }
        if (t instanceof ClassType c && !c.args().isEmpty()) {
            List<Type> args = new ArrayList<>();
            for (Type a : c.args()) {
                args.add(subst(a, m));
            }
            return new ClassType(c.cls(), args, c.nullable());
        }
        if (t instanceof FunctionType f) {
            List<Type> ps = new ArrayList<>();
            for (Type p : f.params()) {
                ps.add(subst(p, m));
            }
            return new FunctionType(ps, subst(f.result(), m), f.nullable());
        }
        return t;
    }

    /** Correspondance paramètres de type de la classe → arguments du type donné. */
    static Map<TypeVar, Type> mapping(Type t) {
        Map<TypeVar, Type> m = new HashMap<>();
        if (t instanceof ClassType ct && ct.cls().typeParams.size() == ct.args().size()) {
            for (int i = 0; i < ct.args().size(); i++) {
                m.put(ct.cls().typeParams.get(i).key(), ct.args().get(i));
            }
        }
        return m;
    }
}
