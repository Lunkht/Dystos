package com.vulsoft.dystos.interp;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Appels de classes Java par réflexion, avec contrôle des classes autorisées.
 * Correspondances : Long -> int/long/short/byte/double..., Double -> double/float, String -> String/char.
 */
final class JavaBridge {

    private static final Object NO_MATCH = new Object();

    private final List<String> allowed;
    private final List<String> denied;

    JavaBridge(List<String> allowed, List<String> denied) {
        this.allowed = List.copyOf(allowed);
        this.denied = List.copyOf(denied);
    }

    boolean isAllowed(String className) {
        for (String d : denied) {
            if (className.startsWith(d)) {
                return false;
            }
        }
        for (String a : allowed) {
            if (className.startsWith(a)) {
                return true;
            }
        }
        return false;
    }

    Class<?> loadClass(String fqcn) {
        if (!isAllowed(fqcn)) {
            return null;
        }
        try {
            return Class.forName(fqcn, false, JavaBridge.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Appels
    // ------------------------------------------------------------------

    Object construct(Class<?> c, List<Object> args) {
        requireAllowed(c);
        Constructor<?> best = null;
        Object[] bestArgs = null;
        int bestCost = Integer.MAX_VALUE;
        for (Constructor<?> k : c.getConstructors()) {
            if (k.getParameterCount() != args.size()) {
                continue;
            }
            Object[] conv = new Object[args.size()];
            int cost = convertAll(k.getParameterTypes(), args, conv);
            if (cost >= 0 && cost < bestCost) {
                best = k;
                bestArgs = conv;
                bestCost = cost;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("aucun constructeur de " + c.getSimpleName() + " ne correspond aux arguments");
        }
        try {
            return fromJava(best.newInstance(bestArgs));
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e.toString());
        }
    }

    Object invokeStatic(Class<?> c, String name, List<Object> args) {
        requireAllowed(c);
        return invokeBest(c, null, name, args, true);
    }

    Object invoke(Object target, String name, List<Object> args) {
        requireAllowed(target.getClass());
        return invokeBest(target.getClass(), target, name, args, false);
    }

    private Object invokeBest(Class<?> c, Object target, String name, List<Object> args, boolean statik) {
        Method best = null;
        Object[] bestArgs = null;
        int bestCost = Integer.MAX_VALUE;
        for (Method m : c.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.size()
                    || Modifier.isStatic(m.getModifiers()) != statik || m.getDeclaringClass() == Object.class
                            && !name.equals("toString") && !name.equals("equals") && !name.equals("hashCode")) {
                continue;
            }
            Object[] conv = new Object[args.size()];
            int cost = convertAll(m.getParameterTypes(), args, conv);
            if (cost >= 0 && cost < bestCost) {
                best = m;
                bestArgs = conv;
                bestCost = cost;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("aucune méthode '" + name + "' de " + c.getSimpleName()
                    + " ne correspond aux " + args.size() + " argument(s) fournis");
        }
        try {
            return fromJava(accessible(best).invoke(target, bestArgs));
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e.toString());
        }
    }

    /** Propriété : getX(), isX() ou champ public ; NOT_FOUND sinon. */
    Object getProperty(Object target, String name) {
        requireAllowed(target.getClass());
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String getter : new String[] {"get" + cap, "is" + cap}) {
            for (Method m : target.getClass().getMethods()) {
                if (m.getName().equals(getter) && m.getParameterCount() == 0 && !Modifier.isStatic(m.getModifiers())) {
                    try {
                        return fromJava(accessible(m).invoke(target));
                    } catch (InvocationTargetException e) {
                        throw unwrap(e);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e.toString());
                    }
                }
            }
        }
        try {
            return fromJava(target.getClass().getField(name).get(target));
        } catch (ReflectiveOperationException e) {
            return Values.NOT_FOUND;
        }
    }

    Object getStatic(Class<?> c, String name) {
        requireAllowed(c);
        try {
            return fromJava(c.getField(name).get(null));
        } catch (ReflectiveOperationException e) {
            return Values.NOT_FOUND;
        }
    }

    boolean hasStaticMethod(Class<?> c, String name) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && Modifier.isStatic(m.getModifiers())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Conversions
    // ------------------------------------------------------------------

    private void requireAllowed(Class<?> c) {
        Class<?> k = c;
        while (k.isArray()) {
            k = k.getComponentType();
        }
        if (k.isPrimitive()) {
            return;
        }
        if (!isAllowed(k.getName()) && !k.getName().equals("java.lang.String")
                && !k.getName().equals("java.lang.Long") && !k.getName().equals("java.lang.Double")
                && !k.getName().equals("java.lang.Boolean") && !k.getName().equals("java.lang.Object")) {
            throw new SecurityException("classe Java non autorisée : " + k.getName());
        }
    }

    /** Convertit tous les arguments ; retourne le coût total, ou -1 si l'un est inconvertible. */
    private int convertAll(Class<?>[] types, List<Object> args, Object[] out) {
        int total = 0;
        for (int i = 0; i < types.length; i++) {
            int[] cost = new int[1];
            Object v = convert(args.get(i), types[i], cost);
            if (v == NO_MATCH) {
                return -1;
            }
            out[i] = v;
            total += cost[0];
        }
        return total;
    }

    private Object convert(Object v, Class<?> t, int[] cost) {
        if (v == null) {
            cost[0] = t.isPrimitive() ? -1 : 0;
            return t.isPrimitive() ? NO_MATCH : null;
        }
        if (t == long.class || t == Long.class) {
            if (v instanceof Long) {
                return v;
            }
        }
        if (t == int.class || t == Integer.class) {
            if (v instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                cost[0] = 1;
                return (int) (long) l;
            }
        }
        if (t == short.class || t == Short.class) {
            if (v instanceof Long l && l >= Short.MIN_VALUE && l <= Short.MAX_VALUE) {
                cost[0] = 2;
                return (short) (long) l;
            }
        }
        if (t == byte.class || t == Byte.class) {
            if (v instanceof Long l && l >= Byte.MIN_VALUE && l <= Byte.MAX_VALUE) {
                cost[0] = 3;
                return (byte) (long) l;
            }
        }
        if (t == double.class || t == Double.class) {
            if (v instanceof Double) {
                return v;
            }
            if (v instanceof Long l) {
                cost[0] = 4;
                return (double) l;
            }
        }
        if (t == float.class || t == Float.class) {
            if (v instanceof Double d) {
                cost[0] = 1;
                return (float) (double) d;
            }
            if (v instanceof Long l) {
                cost[0] = 5;
                return (float) (long) l;
            }
        }
        if ((t == boolean.class || t == Boolean.class) && v instanceof Boolean) {
            return v;
        }
        if ((t == char.class || t == Character.class) && v instanceof String s && s.length() == 1) {
            cost[0] = 1;
            return s.charAt(0);
        }
        if (t == Object.class) {
            cost[0] = 6;
            return v;
        }
        if (t == Number.class && (v instanceof Long || v instanceof Double)) {
            cost[0] = 5;
            return v;
        }
        if (!t.isPrimitive() && t.isInstance(v)) {
            cost[0] = 1;
            return v;
        }
        cost[0] = -1;
        return NO_MATCH;
    }

    /** Valeur Java -> valeur Dystos. */
    Object fromJava(Object o) {
        if (o == null || o instanceof Boolean || o instanceof String) {
            return o;
        }
        if (o instanceof Long || o instanceof Integer || o instanceof Short || o instanceof Byte) {
            return ((Number) o).longValue();
        }
        if (o instanceof Double || o instanceof Float) {
            return ((Number) o).doubleValue();
        }
        if (o instanceof Character) {
            return String.valueOf(o);
        }
        if (o.getClass().isArray()) {
            List<Object> l = new ArrayList<>();
            int n = java.lang.reflect.Array.getLength(o);
            for (int i = 0; i < n; i++) {
                l.add(fromJava(java.lang.reflect.Array.get(o, i)));
            }
            return l;
        }
        requireAllowed(o.getClass());
        return o;
    }

    private static RuntimeException unwrap(InvocationTargetException e) {
        Throwable c = e.getCause();
        if (c instanceof RuntimeException r) {
            return r;
        }
        return new IllegalStateException(String.valueOf(c));
    }

    /** Cherche la même méthode dans un type public (classes d'implémentation non publiques). */
    private static Method accessible(Method m) {
        if (Modifier.isPublic(m.getDeclaringClass().getModifiers())) {
            return m;
        }
        Method found = search(m.getDeclaringClass(), m);
        return found != null ? found : m;
    }

    private static Method search(Class<?> c, Method m) {
        List<Class<?>> supers = new ArrayList<>(List.of(c.getInterfaces()));
        if (c.getSuperclass() != null) {
            supers.add(c.getSuperclass());
        }
        for (Class<?> s : supers) {
            if (Modifier.isPublic(s.getModifiers())) {
                try {
                    return s.getMethod(m.getName(), m.getParameterTypes());
                } catch (NoSuchMethodException ignored) {
                    // on continue la recherche
                }
            }
            Method r = search(s, m);
            if (r != null) {
                return r;
            }
        }
        return null;
    }
}
