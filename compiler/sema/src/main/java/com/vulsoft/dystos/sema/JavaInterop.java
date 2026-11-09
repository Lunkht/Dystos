package com.vulsoft.dystos.sema;

import static com.vulsoft.dystos.sema.Builtins.*;

import com.vulsoft.dystos.sema.Symbols.ClassKind;
import com.vulsoft.dystos.sema.Symbols.ClassSymbol;
import com.vulsoft.dystos.sema.Symbols.FuncSymbol;
import com.vulsoft.dystos.sema.Symbols.ParamSym;
import com.vulsoft.dystos.sema.Symbols.VarKind;
import com.vulsoft.dystos.sema.Symbols.VarSymbol;
import com.vulsoft.dystos.sema.Type.ClassType;
import com.vulsoft.dystos.sema.Type.TypeVar;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pont vers les classes Java : charge, par réflexion et sans les initialiser, les classes dont le
 * nom commence par un préfixe autorisé (par défaut {@code java.} et {@code javax.}).
 *
 * <p>Correspondances de types : {@code String, char -> str} ; {@code byte..long -> int} ;
 * {@code float, double -> float} ; {@code boolean -> bool} ; {@code void -> unit} ;
 * {@code Object -> any}. Les accesseurs {@code getX()} / {@code isX()} sont aussi exposés comme
 * propriétés {@code x}. Les types référence Java sont considérés non nuls.
 */
final class JavaInterop {

    private final Map<Class<?>, ClassSymbol> cache = new HashMap<>();
    private final List<String> allowedPrefixes;
    private final List<String> deniedPrefixes;

    JavaInterop() {
        this(List.of("java.", "javax."), List.of());
    }

    JavaInterop(List<String> allowedPrefixes, List<String> deniedPrefixes) {
        this.allowedPrefixes = List.copyOf(allowedPrefixes);
        this.deniedPrefixes = List.copyOf(deniedPrefixes);
    }

    /** Charge une classe Java par son nom complet, ou retourne null si elle est introuvable ou interdite. */
    ClassSymbol load(String fqcn) {
        boolean allowed = false;
        for (String p : allowedPrefixes) {
            if (fqcn.startsWith(p)) {
                allowed = true;
                break;
            }
        }
        for (String p : deniedPrefixes) {
            if (fqcn.startsWith(p)) {
                allowed = false;
                break;
            }
        }
        if (!allowed) {
            return null;
        }
        try {
            return symbolFor(Class.forName(fqcn, false, JavaInterop.class.getClassLoader()));
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private ClassSymbol symbolFor(Class<?> c) {
        ClassSymbol existing = cache.get(c);
        if (existing != null) {
            return existing;
        }
        ClassSymbol s = new ClassSymbol(c.getSimpleName(), ClassKind.JAVA);
        s.qualifiedName = c.getName();
        s.javaInterface = c.isInterface();
        s.isAbstract = c.isInterface() || Modifier.isAbstract(c.getModifiers());
        for (TypeVariable<?> tv : c.getTypeParameters()) {
            s.typeParams.add(Types.freshVar(tv.getName()));
        }
        cache.put(c, s);
        s.lazyLoader = () -> populate(s, c);
        return s;
    }

    // ------------------------------------------------------------------
    // Membres
    // ------------------------------------------------------------------

    private void populate(ClassSymbol sym, Class<?> c) {
        Map<String, Type> vars = new HashMap<>();
        TypeVariable<?>[] tps = c.getTypeParameters();
        for (int i = 0; i < tps.length; i++) {
            vars.put(tps[i].getName(), sym.typeParams.get(i));
        }

        java.lang.reflect.Type gs = c.getGenericSuperclass();
        if (gs != null && gs != Object.class && mapType(gs, vars) instanceof ClassType ct && ct.cls() != ANY) {
            sym.superType = ct;
        }
        for (java.lang.reflect.Type gi : c.getGenericInterfaces()) {
            if (mapType(gi, vars) instanceof ClassType ct) {
                sym.interfaces.add(ct);
            }
        }

        for (Constructor<?> k : c.getConstructors()) {
            FuncSymbol f = new FuncSymbol("init");
            f.owner = sym;
            f.isConstructor = true;
            f.javaMember = k;
            Map<String, Type> scope = withMethodVars(vars, k.getTypeParameters(), f);
            fillParams(f, k.getGenericParameterTypes(), k.isVarArgs(), scope);
            sym.constructors.add(f);
        }

        Map<String, Method> chosen = new java.util.LinkedHashMap<>();
        for (Method m : c.getMethods()) {
            if (m.isBridge() || m.isSynthetic() || m.getDeclaringClass() == Object.class) {
                continue;
            }
            // Plusieurs surcharges Java peuvent avoir la même signature une fois traduites en types Dystos
            // (int/long/short -> int, float/double -> float, tableaux et Object -> any) : on garde la plus naturelle.
            String key = m.getName() + (Modifier.isStatic(m.getModifiers()) ? "#s" : "#i") + signatureKey(m);
            Method prev = chosen.get(key);
            if (prev == null || rank(m) > rank(prev)) {
                chosen.put(key, m);
            }
        }
        // La résolution prend le premier candidat compatible : du plus spécifique au plus général
        // (int avant float, types précis avant any), comme pour les fonctions prédéfinies min/max.
        List<Method> ordered = new ArrayList<>(chosen.values());
        ordered.sort(java.util.Comparator.comparingInt(this::generality));
        for (Method m : ordered) {
            FuncSymbol f = new FuncSymbol(m.getName());
            f.owner = sym;
            f.isStatic = Modifier.isStatic(m.getModifiers());
            f.javaMember = m;
            Map<String, Type> scope = withMethodVars(vars, m.getTypeParameters(), f);
            fillParams(f, m.getGenericParameterTypes(), m.isVarArgs(), scope);
            f.returnType = mapType(m.getGenericReturnType(), scope);
            sym.addMethod(f);

            String prop = propertyName(m, f.returnType);
            if (prop != null && !f.isStatic && !sym.fields.containsKey(prop)) {
                VarSymbol v = new VarSymbol(prop, f.returnType, false, VarKind.PROPERTY);
                v.owner = sym;
                v.javaGetter = m.getName();
                v.javaMember = m;
                sym.fields.put(prop, v);
            }
        }

        for (Field fd : c.getFields()) {
            VarSymbol v = new VarSymbol(fd.getName(), mapType(fd.getGenericType(), vars),
                    !Modifier.isFinal(fd.getModifiers()), VarKind.FIELD);
            v.owner = sym;
            v.javaMember = fd;
            v.isStatic = Modifier.isStatic(fd.getModifiers());
            sym.fields.putIfAbsent(fd.getName(), v);
        }
    }

    /** Clé de la signature Dystos d'une méthode Java (types traduits). */
    private String signatureKey(Method m) {
        StringBuilder sb = new StringBuilder("(");
        for (java.lang.reflect.Type t : m.getGenericParameterTypes()) {
            sb.append(mapType(t, new HashMap<>()).toString()).append(',');
        }
        return sb.append(')').toString();
    }

    private int generality(Method m) {
        int g = 0;
        for (java.lang.reflect.Type t : m.getGenericParameterTypes()) {
            String s = mapType(t, new HashMap<>()).toString();
            g += s.equals("any") ? 3 : s.equals("float") ? 2 : 1;
        }
        return g;
    }

    /** Préférence entre surcharges de même signature Dystos : types larges, jamais de tableaux. */
    private static int rank(Method m) {
        int r = 0;
        for (Class<?> p : m.getParameterTypes()) {
            if (p.isArray()) {
                r += 0;
            } else if (p == long.class || p == double.class || p == String.class) {
                r += 4;
            } else if (p == int.class || p == float.class || p == boolean.class || p == Object.class || !p.isPrimitive()
                    && !Number.class.isAssignableFrom(p) && p != Character.class) {
                r += 3;
            } else {
                r += 2;
            }
        }
        return r;
    }

    private Map<String, Type> withMethodVars(Map<String, Type> vars, TypeVariable<?>[] methodVars, FuncSymbol f) {
        if (methodVars.length == 0) {
            return vars;
        }
        Map<String, Type> scope = new HashMap<>(vars);
        for (TypeVariable<?> tv : methodVars) {
            TypeVar fresh = Types.freshVar(tv.getName());
            f.typeParams.add(fresh);
            scope.put(tv.getName(), fresh);
        }
        return scope;
    }

    private void fillParams(FuncSymbol f, java.lang.reflect.Type[] types, boolean varArgs, Map<String, Type> scope) {
        for (int i = 0; i < types.length; i++) {
            java.lang.reflect.Type t = types[i];
            boolean last = i == types.length - 1;
            Type mapped;
            if (last && varArgs) {
                f.variadic = true;
                if (t instanceof GenericArrayType g) {
                    mapped = mapType(g.getGenericComponentType(), scope);
                } else if (t instanceof Class<?> k && k.isArray()) {
                    mapped = mapType(k.getComponentType(), scope);
                } else {
                    mapped = ANY_T;
                }
            } else {
                mapped = mapType(t, scope);
            }
            f.params.add(new ParamSym("p" + i, mapped, false));
        }
    }

    private static String propertyName(Method m, Type returnType) {
        if (m.getParameterCount() != 0) {
            return null;
        }
        String n = m.getName();
        if (n.length() > 3 && n.startsWith("get") && Character.isUpperCase(n.charAt(3))) {
            return decapitalize(n.substring(3));
        }
        if (n.length() > 2 && n.startsWith("is") && Character.isUpperCase(n.charAt(2)) && Types.isBool(returnType)) {
            return decapitalize(n.substring(2));
        }
        return null;
    }

    private static String decapitalize(String s) {
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------
    // Correspondance des types
    // ------------------------------------------------------------------

    private Type mapType(java.lang.reflect.Type t, Map<String, Type> vars) {
        if (t instanceof Class<?> k) {
            return mapClass(k);
        }
        if (t instanceof ParameterizedType p) {
            Type raw = mapClass((Class<?>) p.getRawType());
            java.lang.reflect.Type[] actual = p.getActualTypeArguments();
            if (raw instanceof ClassType rc && rc.cls().typeParams.size() == actual.length) {
                List<Type> args = new ArrayList<>();
                for (java.lang.reflect.Type a : actual) {
                    args.add(mapType(a, vars));
                }
                return new ClassType(rc.cls(), args, false);
            }
            return raw;
        }
        if (t instanceof TypeVariable<?> v) {
            Type r = vars.get(v.getName());
            return r != null ? r : ANY_T;
        }
        if (t instanceof WildcardType w) {
            java.lang.reflect.Type[] ub = w.getUpperBounds();
            return ub.length > 0 ? mapType(ub[0], vars) : ANY_T;
        }
        return ANY_T;
    }

    private Type mapClass(Class<?> k) {
        if (k == void.class || k == Void.class) {
            return Type.UNIT;
        }
        if (k == boolean.class || k == Boolean.class) {
            return BOOL_T;
        }
        if (k == byte.class || k == short.class || k == int.class || k == long.class
                || k == Byte.class || k == Short.class || k == Integer.class || k == Long.class) {
            return INT_T;
        }
        if (k == float.class || k == double.class || k == Float.class || k == Double.class) {
            return FLOAT_T;
        }
        if (k == char.class || k == Character.class || k == String.class) {
            return STR_T;
        }
        if (k == Object.class || k.isArray()) {
            return ANY_T;
        }
        ClassSymbol s = symbolFor(k);
        return new ClassType(s, new ArrayList<Type>(Collections.nCopies(s.typeParams.size(), ANY_T)), false);
    }
}
