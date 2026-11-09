package com.vulsoft.dystos.interp;

import com.vulsoft.dystos.interp.Values.BuiltinFn;
import com.vulsoft.dystos.interp.Values.EntryValue;
import com.vulsoft.dystos.interp.Values.NativeFn;
import com.vulsoft.dystos.interp.Values.TaskValue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Fonctions prédéfinies, méthodes natives (str, list, map, set...) et modules io / concurrent. */
final class Natives {

    private Natives() {
    }

    // ------------------------------------------------------------------
    // Propriétés et méthodes
    // ------------------------------------------------------------------

    static Object property(Interpreter in, Object t, String name) {
        if (t instanceof String s) {
            return name.equals("length") ? (Object) (long) s.length() : Values.NOT_FOUND;
        }
        if (t instanceof EntryValue e) {
            if (name.equals("key")) {
                return e.key();
            }
            if (name.equals("value")) {
                return e.value();
            }
            return Values.NOT_FOUND;
        }
        if (t.getClass().getName().startsWith("java.")) {
            return in.java.getProperty(t, name);
        }
        return Values.NOT_FOUND;
    }

    @SuppressWarnings("unchecked")
    static Object invoke(Interpreter in, Object t, String name, List<Object> a) {
        if (t instanceof String s) {
            return stringMethod(in, s, name, a);
        }
        if (t instanceof List<?> l) {
            Object r = listMethod(in, (List<Object>) l, name, a);
            return r != Values.NOT_FOUND ? r : in.java.invoke(t, name, a);
        }
        if (t instanceof Map<?, ?> m) {
            Object r = mapMethod(in, (Map<Object, Object>) m, name, a);
            return r != Values.NOT_FOUND ? r : in.java.invoke(t, name, a);
        }
        if (t instanceof Set<?> s) {
            Object r = setMethod(in, (Set<Object>) s, name, a);
            return r != Values.NOT_FOUND ? r : in.java.invoke(t, name, a);
        }
        if (t instanceof FileObj f) {
            return f.call(name, a);
        }
        if (t instanceof TaskValue tv && name.equals("await")) {
            if (tv.error != null) {
                throw tv.error;
            }
            return tv.result;
        }
        if (name.equals("toString") && a.isEmpty()) {
            return in.str(t);
        }
        if (t.getClass().getName().startsWith("java.")) {
            return in.java.invoke(t, name, a);
        }
        throw in.rt("méthode '" + name + "' inconnue pour " + in.str(t));
    }

    private static Object stringMethod(Interpreter in, String s, String name, List<Object> a) {
        switch (name) {
            case "upper": return s.toUpperCase();
            case "lower": return s.toLowerCase();
            case "strip": return s.strip();
            case "split": {
                String sep = (String) a.get(0);
                if (sep.isEmpty()) {
                    throw in.rt("séparateur vide");
                }
                List<Object> out = new ArrayList<>();
                for (String p : s.split(Pattern.quote(sep), -1)) {
                    out.add(p);
                }
                return out;
            }
            case "join": {
                StringBuilder sb = new StringBuilder();
                boolean first = true;
                for (Object o : in.iterableToList(a.get(0))) {
                    sb.append(first ? "" : s).append(in.str(o));
                    first = false;
                }
                return sb.toString();
            }
            case "startswith": return s.startsWith((String) a.get(0));
            case "endswith": return s.endsWith((String) a.get(0));
            case "contains": return s.contains((String) a.get(0));
            case "replace": return s.replace((String) a.get(0), (String) a.get(1));
            case "lines": {
                List<Object> out = new ArrayList<>();
                s.lines().forEach(out::add);
                return out;
            }
            case "toString": return s;
            default: throw in.rt("méthode '" + name + "' inconnue pour str");
        }
    }

    private static Object listMethod(Interpreter in, List<Object> l, String name, List<Object> a) {
        switch (name) {
            case "add":
                if (a.size() == 1) {
                    l.add(a.get(0));
                    return null;
                }
                return Values.NOT_FOUND;
            case "contains":
                return listContains(in, l, a.get(0));
            case "pop":
                if (l.isEmpty()) {
                    throw in.rt("pop() sur une liste vide");
                }
                return l.remove(l.size() - 1);
            case "clear":
                l.clear();
                return null;
            case "extend":
                l.addAll(in.iterableToList(a.get(0)));
                return null;
            default:
                return Values.NOT_FOUND;
        }
    }

    private static boolean listContains(Interpreter in, List<Object> l, Object x) {
        for (Object o : l) {
            if (in.valuesEqual(o, x)) {
                return true;
            }
        }
        return false;
    }

    private static Object mapMethod(Interpreter in, Map<Object, Object> m, String name, List<Object> a) {
        switch (name) {
            case "items": {
                List<Object> out = new ArrayList<>();
                for (Map.Entry<Object, Object> e : m.entrySet()) {
                    out.add(new EntryValue(e.getKey(), e.getValue()));
                }
                return out;
            }
            case "keys": return new ArrayList<>(m.keySet());
            case "values": return new ArrayList<>(m.values());
            case "get": return m.get(a.get(0));
            case "contains": return m.containsKey(a.get(0));
            case "remove":
                m.remove(a.get(0));
                return null;
            default: return Values.NOT_FOUND;
        }
    }

    private static Object setMethod(Interpreter in, Set<Object> s, String name, List<Object> a) {
        switch (name) {
            case "add":
                s.add(a.get(0));
                return null;
            case "contains": return s.contains(a.get(0));
            case "remove":
                s.remove(a.get(0));
                return null;
            default: return Values.NOT_FOUND;
        }
    }

    // ------------------------------------------------------------------
    // Fonctions prédéfinies
    // ------------------------------------------------------------------

    static Object builtin(Interpreter in, String name, List<Object> a, Map<String, Object> named) {
        switch (name) {
            case "print": {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < a.size(); i++) {
                    sb.append(i > 0 ? " " : "").append(in.str(a.get(i)));
                }
                in.print(sb.append('\n').toString());
                return null;
            }
            case "len": {
                Object v = a.get(0);
                if (v instanceof String s) {
                    return (long) s.length();
                }
                if (v instanceof java.util.Collection<?> c) {
                    return (long) c.size();
                }
                if (v instanceof Map<?, ?> m) {
                    return (long) m.size();
                }
                throw in.rt("len() non applicable à " + in.str(v));
            }
            case "range": {
                long lo = a.size() == 1 ? 0 : (Long) a.get(0);
                long hi = a.size() == 1 ? (Long) a.get(0) : (Long) a.get(1);
                long step = a.size() == 3 ? (Long) a.get(2) : 1;
                if (step == 0) {
                    throw in.rt("le pas de range() ne peut pas être 0");
                }
                List<Object> out = new ArrayList<>();
                for (long i = lo; step > 0 ? i < hi : i > hi; i += step) {
                    out.add(i);
                    if (out.size() > 5_000_000) {
                        throw in.rt("range() trop grand");
                    }
                }
                return out;
            }
            case "sum": {
                long li = 0;
                double d = 0;
                boolean isDouble = false;
                for (Object o : in.iterableToList(a.get(0))) {
                    if (o instanceof Double x) {
                        isDouble = true;
                        d += x;
                    } else {
                        li = Math.addExact(li, (Long) o);
                        d += (Long) o;
                    }
                }
                return isDouble ? (Object) d : (Object) li;
            }
            case "abs": {
                Object v = a.get(0);
                return v instanceof Long l ? (Object) Math.abs(l) : (Object) Math.abs((Double) v);
            }
            case "min":
                return in.compare(a.get(0), a.get(1)) <= 0 ? a.get(0) : a.get(1);
            case "max":
                return in.compare(a.get(0), a.get(1)) >= 0 ? a.get(0) : a.get(1);
            case "sorted": {
                List<Object> items = in.iterableToList(a.get(0));
                Object key = named.containsKey("key") ? named.get("key") : a.size() > 1 ? a.get(1) : null;
                if (key == null) {
                    items.sort(in::compare);
                } else {
                    List<Object[]> decorated = new ArrayList<>();
                    for (Object o : items) {
                        decorated.add(new Object[] {in.callValue(key, new ArrayList<>(List.of(o)), Map.of()), o});
                    }
                    decorated.sort((x, y) -> in.compare(x[0], y[0]));
                    items = new ArrayList<>();
                    for (Object[] d : decorated) {
                        items.add(d[1]);
                    }
                }
                return items;
            }
            case "filter": {
                List<Object> out = new ArrayList<>();
                for (Object o : in.iterableToList(a.get(1))) {
                    if (Boolean.TRUE.equals(in.callValue(a.get(0), new ArrayList<>(List.of(o)), Map.of()))) {
                        out.add(o);
                    }
                }
                return out;
            }
            case "enumerate": {
                List<Object> out = new ArrayList<>();
                long i = 0;
                for (Object o : in.iterableToList(a.get(0))) {
                    out.add(new EntryValue(i++, o));
                }
                return out;
            }
            case "str":
                return in.str(a.get(0));
            case "int": {
                Object v = a.get(0);
                if (v instanceof Long) {
                    return v;
                }
                if (v instanceof Double d) {
                    if (d.isNaN() || d.isInfinite()) {
                        throw in.valueError("conversion impossible en int : " + in.str(v));
                    }
                    return (long) (double) d;
                }
                if (v instanceof Boolean b) {
                    return b ? 1L : 0L;
                }
                try {
                    return Long.parseLong(((String) v).strip());
                } catch (NumberFormatException e) {
                    throw in.valueError("conversion impossible en int : " + in.quoted(v));
                }
            }
            case "float": {
                Object v = a.get(0);
                if (v instanceof Double) {
                    return v;
                }
                if (v instanceof Long l) {
                    return (double) l;
                }
                try {
                    return Double.parseDouble(((String) v).strip());
                } catch (NumberFormatException | ClassCastException e) {
                    throw in.valueError("conversion impossible en float : " + in.quoted(v));
                }
            }
            case "bool": {
                Object v = a.get(0);
                if (v == null) {
                    return false;
                }
                if (v instanceof Boolean) {
                    return v;
                }
                if (v instanceof Long l) {
                    return l != 0;
                }
                if (v instanceof Double d) {
                    return d != 0;
                }
                if (v instanceof String s) {
                    return !s.isEmpty();
                }
                if (v instanceof java.util.Collection<?> c) {
                    return !c.isEmpty();
                }
                if (v instanceof Map<?, ?> m) {
                    return !m.isEmpty();
                }
                return true;
            }
            default:
                throw in.rt("fonction prédéfinie inconnue : " + name);
        }
    }

    // ------------------------------------------------------------------
    // Modules
    // ------------------------------------------------------------------

    static Map<String, Object> ioModule(Interpreter in) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("open", new NativeFn("open", (args, named) -> {
            if (!in.opts.allowFiles()) {
                throw in.rt("l'accès aux fichiers est désactivé dans ce mode d'exécution");
            }
            String p = (String) args.get(0);
            String mode = args.size() > 1 ? (String) args.get(1) : named.containsKey("mode") ? (String) named.get("mode") : "r";
            if (!mode.equals("r") && !mode.equals("w") && !mode.equals("a")) {
                throw in.rt("mode d'ouverture inconnu : '" + mode + "' (r, w ou a)");
            }
            Path base = in.opts.workDir().toAbsolutePath().normalize();
            Path resolved = base.resolve(p).normalize();
            if (!resolved.startsWith(base)) {
                throw in.rt("chemin hors du répertoire de travail : " + p);
            }
            if (mode.equals("r") && !Files.isRegularFile(resolved)) {
                throw in.rt("fichier introuvable : " + p);
            }
            return new FileObj(in, resolved, mode);
        }));
        return m;
    }

    static Map<String, Object> concurrentModule(Interpreter in) {
        Map<String, Object> m = new LinkedHashMap<>();
        // Exécution séquentielle pour l'instant : le vrai parallélisme viendra avec le backend JVM.
        m.put("run", new NativeFn("run", (args, named) -> {
            TaskValue t = new TaskValue();
            try {
                t.result = in.callValue(args.get(0), new ArrayList<>(), Map.of());
            } catch (Interpreter.DystosThrow e) {
                t.error = e;
            }
            return t;
        }));
        return m;
    }

    static final class FileObj {
        private final Interpreter in;
        private final Path path;
        private final String mode;
        private final StringBuilder buf = new StringBuilder();
        private boolean closed;

        FileObj(Interpreter in, Path path, String mode) {
            this.in = in;
            this.path = path;
            this.mode = mode;
        }

        Object call(String name, List<Object> a) {
            try {
                switch (name) {
                    case "write":
                        if (mode.equals("r") || closed) {
                            throw in.rt("fichier non ouvert en écriture");
                        }
                        buf.append((String) a.get(0));
                        return null;
                    case "read":
                        requireReadable();
                        return Files.readString(path);
                    case "lines": {
                        requireReadable();
                        List<Object> out = new ArrayList<>(Files.readAllLines(path));
                        return out;
                    }
                    case "close":
                        if (!closed && !mode.equals("r")) {
                            Files.writeString(path, buf, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                                    mode.equals("a") ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
                        }
                        closed = true;
                        return null;
                    default:
                        throw in.rt("méthode '" + name + "' inconnue pour File");
                }
            } catch (IOException e) {
                throw in.rt("erreur d'entrée/sortie : " + e.getMessage());
            }
        }

        private void requireReadable() {
            if (!mode.equals("r") || closed) {
                throw in.rt("fichier non ouvert en lecture");
            }
        }
    }
}
