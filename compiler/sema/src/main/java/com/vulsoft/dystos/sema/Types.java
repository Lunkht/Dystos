package com.vulsoft.dystos.sema;

import static com.vulsoft.dystos.sema.Builtins.*;

import com.vulsoft.dystos.sema.Symbols.ClassSymbol;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Algorithmes sur les types : compatibilité, substitution, unification, hiérarchie. */
final class Types {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private Types() {
    }

    static TypeVar freshVar(String name) {
        return new TypeVar(name, COUNTER.incrementAndGet(), false);
    }

    // ------------------------------------------------------------------
    // Prédicats
    // ------------------------------------------------------------------

    static boolean isClass(Type t, ClassSymbol c) {
        return t instanceof ClassType ct && ct.cls() == c && !ct.nullable();
    }

    static boolean isInt(Type t) {
        return isClass(t, INT);
    }

    static boolean isFloat(Type t) {
        return isClass(t, FLOAT);
    }

    static boolean isNumeric(Type t) {
        return isInt(t) || isFloat(t);
    }

    static boolean isBool(Type t) {
        return isClass(t, BOOL);
    }

    static boolean isStr(Type t) {
        return isClass(t, STR);
    }

    static Type nonNull(Type t) {
        return t instanceof NullType ? t : t.withNullable(false);
    }

    // ------------------------------------------------------------------
    // Hiérarchie
    // ------------------------------------------------------------------

    static Map<TypeVar, Type> mapping(ClassType t) {
        Map<TypeVar, Type> m = new HashMap<>();
        List<TypeVar> ps = t.cls().typeParams;
        if (ps.size() == t.args().size()) {
            for (int i = 0; i < ps.size(); i++) {
                m.put(ps.get(i).key(), t.args().get(i));
            }
        }
        return m;
    }

    /** Vue de {@code t} comme instance de {@code target} (avec arguments de type), ou null. */
    static ClassType asSuper(ClassType t, ClassSymbol target) {
        return asSuper(t, target, 0);
    }

    private static ClassType asSuper(ClassType t, ClassSymbol target, int depth) {
        if (t.cls() == target) {
            return t;
        }
        if (depth > 64) {
            return null;
        }
        t.cls().ensureLoaded();
        Map<TypeVar, Type> m = mapping(t);
        if (t.cls().superType != null) {
            Type s = substitute(t.cls().superType, m);
            if (s instanceof ClassType sc) {
                ClassType r = asSuper(sc, target, depth + 1);
                if (r != null) {
                    return r;
                }
            }
        }
        for (ClassType i : t.cls().interfaces) {
            Type s = substitute(i, m);
            if (s instanceof ClassType sc) {
                ClassType r = asSuper(sc, target, depth + 1);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    /** {@code t} puis tous ses super-types (avec substitution), puis {@code any}. */
    static List<ClassType> linearize(ClassType t) {
        List<ClassType> out = new ArrayList<>();
        List<ClassType> queue = new ArrayList<>();
        queue.add(t);
        while (!queue.isEmpty() && out.size() < 128) {
            ClassType c = queue.remove(0);
            boolean seen = false;
            for (ClassType o : out) {
                if (o.cls() == c.cls()) {
                    seen = true;
                    break;
                }
            }
            if (seen) {
                continue;
            }
            out.add(c);
            c.cls().ensureLoaded();
            Map<TypeVar, Type> m = mapping(c);
            if (c.cls().superType != null && substitute(c.cls().superType, m) instanceof ClassType s) {
                queue.add(s);
            }
            for (ClassType i : c.cls().interfaces) {
                if (substitute(i, m) instanceof ClassType s) {
                    queue.add(s);
                }
            }
        }
        boolean hasAny = false;
        for (ClassType o : out) {
            if (o.cls() == ANY) {
                hasAny = true;
            }
        }
        if (!hasAny) {
            out.add(ANY_T);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Compatibilité
    // ------------------------------------------------------------------

    static boolean isAssignable(Type from, Type to) {
        if (from instanceof ErrorType || to instanceof ErrorType) {
            return true;
        }
        if (from instanceof NullType) {
            return to.nullable();
        }
        if (to instanceof NullType) {
            return false;
        }
        if (from.nullable() && !to.nullable()) {
            return false;
        }
        if (from instanceof UnitType || to instanceof UnitType) {
            return from instanceof UnitType && to instanceof UnitType;
        }
        if (to instanceof ClassType tc && tc.cls() == ANY) {
            return true;
        }
        if (from instanceof TypeVar fv) {
            return to instanceof TypeVar tv && fv.id() == tv.id();
        }
        if (to instanceof TypeVar) {
            return false;
        }
        if (from instanceof FunctionType ff) {
            if (!(to instanceof FunctionType tf) || ff.params().size() != tf.params().size()) {
                return false;
            }
            for (int i = 0; i < ff.params().size(); i++) {
                if (!isAssignable(tf.params().get(i), ff.params().get(i))) {
                    return false;
                }
            }
            return tf.result() instanceof UnitType || isAssignable(ff.result(), tf.result());
        }
        if (from instanceof ClassType fc && to instanceof ClassType tc) {
            if (fc.cls() == INT && tc.cls() == FLOAT) {
                return true;
            }
            ClassType sup = asSuper(fc, tc.cls());
            if (sup == null) {
                return false;
            }
            if (sup.args().size() != tc.args().size()) {
                return true;
            }
            for (int i = 0; i < tc.args().size(); i++) {
                if (!sameType(sup.args().get(i), tc.args().get(i))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    static boolean sameType(Type a, Type b) {
        if (a instanceof ErrorType || b instanceof ErrorType) {
            return true;
        }
        return a.equals(b);
    }

    // ------------------------------------------------------------------
    // Substitution et unification
    // ------------------------------------------------------------------

    static Type substitute(Type t, Map<TypeVar, Type> m) {
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
        if (t instanceof ClassType c) {
            if (c.args().isEmpty()) {
                return t;
            }
            List<Type> args = new ArrayList<>();
            for (Type a : c.args()) {
                args.add(substitute(a, m));
            }
            return new ClassType(c.cls(), args, c.nullable());
        }
        if (t instanceof FunctionType f) {
            List<Type> ps = new ArrayList<>();
            for (Type p : f.params()) {
                ps.add(substitute(p, m));
            }
            return new FunctionType(ps, substitute(f.result(), m), f.nullable());
        }
        return t;
    }

    static boolean containsVar(Type t, Set<TypeVar> vars) {
        if (t instanceof TypeVar tv) {
            return vars.contains(tv.key());
        }
        if (t instanceof ClassType c) {
            for (Type a : c.args()) {
                if (containsVar(a, vars)) {
                    return true;
                }
            }
        }
        if (t instanceof FunctionType f) {
            for (Type p : f.params()) {
                if (containsVar(p, vars)) {
                    return true;
                }
            }
            return containsVar(f.result(), vars);
        }
        return false;
    }

    /** Remplace les variables listées par ERROR. */
    static Type eraseVars(Type t, Set<TypeVar> vars) {
        if (vars.isEmpty()) {
            return t;
        }
        Map<TypeVar, Type> m = new HashMap<>();
        for (TypeVar v : vars) {
            m.put(v.key(), Type.ERROR);
        }
        return substitute(t, m);
    }

    /**
     * Unifie un paramètre (qui peut contenir des variables à inférer) avec un argument réel.
     * Complète {@code b}. Retourne false si l'argument est incompatible.
     */
    static boolean unify(Type param, Type arg, Set<TypeVar> inferable, Map<TypeVar, Type> b) {
        if (arg instanceof ErrorType || param instanceof ErrorType) {
            return true;
        }
        if (arg instanceof NullType) {
            return param.nullable();
        }
        if (param instanceof TypeVar tv && inferable.contains(tv.key())) {
            Type a = tv.nullable() ? nonNull(arg) : arg;
            if (a instanceof UnitType) {
                return false;
            }
            Type cur = b.get(tv.key());
            if (cur == null) {
                b.put(tv.key(), a);
                return true;
            }
            if (isAssignable(a, cur)) {
                return true;
            }
            if (isAssignable(cur, a)) {
                b.put(tv.key(), a);
                return true;
            }
            return false;
        }
        if (param instanceof ClassType pc && arg instanceof ClassType ac) {
            if (ac.nullable() && !pc.nullable()) {
                return false;
            }
            if (pc.cls() == ANY) {
                return true;
            }
            if (ac.cls() == INT && pc.cls() == FLOAT) {
                return true;
            }
            ClassType sup = asSuper(ac, pc.cls());
            if (sup == null) {
                return false;
            }
            if (sup.args().size() != pc.args().size()) {
                return true;
            }
            for (int i = 0; i < pc.args().size(); i++) {
                if (!unify(pc.args().get(i), sup.args().get(i), inferable, b)) {
                    return false;
                }
            }
            return true;
        }
        if (param instanceof FunctionType pf && arg instanceof FunctionType af) {
            if (pf.params().size() != af.params().size()) {
                return false;
            }
            for (int i = 0; i < pf.params().size(); i++) {
                if (!unify(pf.params().get(i), af.params().get(i), inferable, b)) {
                    return false;
                }
            }
            if (pf.result() instanceof UnitType) {
                return true;
            }
            return unify(pf.result(), af.result(), inferable, b);
        }
        return isAssignable(arg, substitute(param, b));
    }

    /** Type commun à deux branches. */
    static Type join(Type a, Type b) {
        if (a instanceof ErrorType) {
            return b;
        }
        if (b instanceof ErrorType) {
            return a;
        }
        if (a instanceof NullType) {
            return b instanceof NullType ? b : b.withNullable(true);
        }
        if (b instanceof NullType) {
            return a.withNullable(true);
        }
        if (isAssignable(a, b)) {
            return b;
        }
        if (isAssignable(b, a)) {
            return a;
        }
        if (a instanceof ClassType ac && b instanceof ClassType bc) {
            for (ClassType k : linearize(ac)) {
                if (k.cls() != ANY && asSuper(bc, k.cls()) != null) {
                    return k.withNullable(ac.nullable() || bc.nullable());
                }
            }
        }
        return a.nullable() || b.nullable() ? ANY_N : ANY_T;
    }
}
