package dystos.rt;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bibliothèque d'exécution des programmes Dystos compilés vers la JVM.
 *
 * <p>Les méthodes sont appelées par le code généré ; leur nom et leur descripteur font partie du contrat avec le
 * compilateur (le compilateur vérifie à la compilation que chaque méthode utilisée existe). Elles reprennent les
 * règles de l'interpréteur : affichage, formats, indices négatifs, messages d'erreur.
 * Représentation : {@code int}=long, {@code float}=double, {@code bool}=boolean, {@code str}=String,
 * {@code list/map/set}=ArrayList/LinkedHashMap/LinkedHashSet ; éléments de collections encadrés (Long, Double, Boolean).
 */
public final class Rt {

    private static final PrintStream OUT = new PrintStream(
            new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 1 << 16), false, StandardCharsets.UTF_8);
    private static final PrintStream ERR = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);

    private Rt() {
    }

    // ------------------------------------------------------------------
    // Programme : sortie, arrêt
    // ------------------------------------------------------------------

    public static void print(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            sb.append(i > 0 ? " " : "").append(str(args[i]));
        }
        OUT.print(sb.append('\n'));
    }

    public static void flush() {
        OUT.flush();
    }

    /** Exception non rattrapée : message sur stderr, code de sortie 1. */
    public static void crash(Throwable t) {
        OUT.flush();
        if (t instanceof DException) {
            DException d = (DException) t;
            ERR.println("Erreur non rattrapée" + where(t) + " : " + d.typeName() + (d.message.isEmpty() ? "" : " : " + d.message));
        } else if (t instanceof StackOverflowError) {
            ERR.println("Erreur non rattrapée" + where(t) + " : RuntimeError : profondeur de récursion maximale dépassée");
        } else {
            ERR.println("Erreur interne : " + t);
        }
        ERR.flush();
        Runtime.getRuntime().halt(1);
    }

    /** « (ligne N) » : première ligne source du programme dans la pile (hors bibliothèque d'exécution), ou rien. */
    private static String where(Throwable t) {
        for (StackTraceElement e : t.getStackTrace()) {
            if (!e.getClassName().startsWith("dystos.rt.") && e.getLineNumber() > 0) {
                return " (ligne " + e.getLineNumber() + ")";
            }
        }
        return "";
    }

    public static DRuntimeError rt(String message) {
        return new DRuntimeError(message);
    }

    public static DValueError valueError(String message) {
        return new DValueError(message);
    }

    public static void assertFail(Object message) {
        throw rt("assertion échouée" + (message != null ? " : " + str(message) : ""));
    }

    // ------------------------------------------------------------------
    // Texte
    // ------------------------------------------------------------------

    public static String str(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof String) {
            return (String) v;
        }
        return repr(v, 0);
    }

    public static String strLong(long v) {
        return Long.toString(v);
    }

    public static String strDouble(double v) {
        return formatDouble(v);
    }

    public static String strBool(boolean v) {
        return v ? "true" : "false";
    }

    public static String repr(Object v) {
        return repr(v, 0);
    }

    /** Comme {@link #str} mais avec des guillemets autour des chaînes (éléments de collections, messages d'erreur). */
    public static String reprQuoted(Object v) {
        return repr(v, 1);
    }

    private static String repr(Object v, int level) {
        if (v == null) {
            return "null";
        }
        if (v instanceof String) {
            return level == 0 ? (String) v : quote((String) v);
        }
        if (v instanceof Boolean) {
            return ((Boolean) v) ? "true" : "false";
        }
        if (v instanceof Long) {
            return v.toString();
        }
        if (v instanceof Double) {
            return formatDouble((Double) v);
        }
        if (level > 12) {
            return "...";
        }
        if (v instanceof List) {
            List<?> l = (List<?>) v;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < l.size(); i++) {
                sb.append(i > 0 ? ", " : "").append(repr(l.get(i), level + 1));
            }
            return sb.append(']').toString();
        }
        if (v instanceof Set) {
            Set<?> s = (Set<?>) v;
            if (s.isEmpty()) {
                return "set()";
            }
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Object o : s) {
                sb.append(first ? "" : ", ").append(repr(o, level + 1));
                first = false;
            }
            return sb.append('}').toString();
        }
        if (v instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> en : ((Map<?, ?>) v).entrySet()) {
                sb.append(first ? "" : ", ").append(repr(en.getKey(), level + 1)).append(": ")
                        .append(repr(en.getValue(), level + 1));
                first = false;
            }
            return sb.append('}').toString();
        }
        String s = String.valueOf(v);
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
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

    /** Flottant à la manière de Python (13.0, 0.1, 1e+16). */
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
        int ae = Math.abs(exp);
        return mant + "e" + (exp < 0 ? "-" : "+") + (ae < 10 ? "0" + ae : String.valueOf(ae));
    }

    public static String format(Object v, String spec) {
        return Fmt.format(v, spec);
    }

    // ------------------------------------------------------------------
    // Arithmétique
    // ------------------------------------------------------------------

    public static long add(long a, long b) {
        long r = a + b;
        if (((a ^ r) & (b ^ r)) < 0) {
            throw rt("débordement d'entier");
        }
        return r;
    }

    public static long sub(long a, long b) {
        long r = a - b;
        if (((a ^ b) & (a ^ r)) < 0) {
            throw rt("débordement d'entier");
        }
        return r;
    }

    public static long mul(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            throw rt("débordement d'entier");
        }
    }

    public static long neg(long a) {
        if (a == Long.MIN_VALUE) {
            throw rt("débordement d'entier");
        }
        return -a;
    }

    public static double divInt(long a, long b) {
        if (b == 0) {
            throw rt("division par zéro");
        }
        return (double) a / (double) b;
    }

    public static double divFloat(double a, double b) {
        if (b == 0) {
            throw rt("division par zéro");
        }
        return a / b;
    }

    public static long modInt(long a, long b) {
        if (b == 0) {
            throw rt("modulo par zéro");
        }
        return Math.floorMod(a, b);
    }

    public static double modFloat(double a, double b) {
        if (b == 0) {
            throw rt("modulo par zéro");
        }
        return a - Math.floor(a / b) * b;
    }

    public static long powInt(long base, long exp) {
        if (exp < 0) {
            throw rt("exposant négatif : utilisez un flottant (ex. 2.0 ** -1)");
        }
        long result = 1;
        for (long i = 0; i < exp; i++) {
            result = mul(result, base);
            if (i > 4096) {
                throw rt("débordement d'entier");
            }
        }
        return result;
    }

    public static double powFloat(double a, double b) {
        return Math.pow(a, b);
    }

    // ------------------------------------------------------------------
    // Égalité, comparaison, appartenance
    // ------------------------------------------------------------------

    public static boolean eq(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if ((a instanceof Long || a instanceof Double) && (b instanceof Long || b instanceof Double)) {
            if (a instanceof Long && b instanceof Long) {
                return ((Long) a).longValue() == ((Long) b).longValue();
            }
            return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        }
        if (a instanceof List && b instanceof List) {
            List<?> x = (List<?>) a;
            List<?> y = (List<?>) b;
            if (x.size() != y.size()) {
                return false;
            }
            for (int i = 0; i < x.size(); i++) {
                if (!eq(x.get(i), y.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof DObject || b instanceof DObject) {
            return a == b;
        }
        return a.equals(b);
    }

    public static boolean same(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a == b || (a instanceof Long || a instanceof Boolean || a instanceof String) && a.equals(b);
    }

    public static int cmp(Object a, Object b) {
        if (a instanceof Long && b instanceof Long) {
            return Long.compare((Long) a, (Long) b);
        }
        if ((a instanceof Long || a instanceof Double) && (b instanceof Long || b instanceof Double)) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        if (a instanceof String && b instanceof String) {
            return ((String) a).compareTo((String) b);
        }
        throw rt("comparaison impossible entre " + str(a) + " et " + str(b));
    }

    public static int cmpStr(String a, String b) {
        return a.compareTo(b);
    }

    public static boolean contains(Object container, Object item) {
        if (container instanceof String && item instanceof String) {
            return ((String) container).contains((String) item);
        }
        if (container instanceof List) {
            for (Object o : (List<?>) container) {
                if (eq(o, item)) {
                    return true;
                }
            }
            return false;
        }
        if (container instanceof Set) {
            Set<?> s = (Set<?>) container;
            if (s.contains(item)) {
                return true;
            }
            for (Object o : s) {
                if (eq(o, item)) {
                    return true;
                }
            }
            return false;
        }
        if (container instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) container;
            if (m.containsKey(item)) {
                return true;
            }
            for (Object o : m.keySet()) {
                if (eq(o, item)) {
                    return true;
                }
            }
            return false;
        }
        throw rt("'in' non applicable à " + str(container));
    }

    // ------------------------------------------------------------------
    // Indices et tranches
    // ------------------------------------------------------------------

    private static int checkIndex(long i, int size) {
        long k = i < 0 ? i + size : i;
        if (k < 0 || k >= size) {
            throw rt("indice hors limites : " + i + " (taille " + size + ")");
        }
        return (int) k;
    }

    public static Object list_getItem(List<Object> l, long i) {
        return l.get(checkIndex(i, l.size()));
    }

    public static void list_setItem(List<Object> l, long i, Object v) {
        l.set(checkIndex(i, l.size()), v);
    }

    public static Object map_getItem(Map<Object, Object> m, Object key) {
        if (m.containsKey(key)) {
            return m.get(key);
        }
        for (Map.Entry<Object, Object> en : m.entrySet()) {
            if (eq(en.getKey(), key)) {
                return en.getValue();
            }
        }
        throw rt("clé introuvable : " + repr(key, 1));
    }

    public static void map_setItem(Map<Object, Object> m, Object key, Object v) {
        m.put(key, v);
    }

    public static String str_getItem(String s, long i) {
        return String.valueOf(s.charAt(checkIndex(i, s.length())));
    }

    private static List<Integer> sliceIndices(int n, Long lo, Long hi, Long st) {
        long step = st == null ? 1 : st;
        if (step == 0) {
            throw rt("le pas d'une tranche ne peut pas être 0");
        }
        List<Integer> idx = new ArrayList<>();
        if (step > 0) {
            long a = lo == null ? 0 : clamp(lo < 0 ? lo + n : lo, 0, n);
            long b = hi == null ? n : clamp(hi < 0 ? hi + n : hi, 0, n);
            for (long i = a; i < b; i += step) {
                idx.add((int) i);
            }
        } else {
            long a = lo == null ? n - 1 : clamp(lo < 0 ? lo + n : lo, -1, n - 1);
            long b = hi == null ? -1 : clamp(hi < 0 ? hi + n : hi, -1, n - 1);
            for (long i = a; i > b; i += step) {
                idx.add((int) i);
            }
        }
        return idx;
    }

    private static long clamp(long v, long lo, long hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static List<Object> list_slice(List<Object> l, Long lo, Long hi, Long step) {
        List<Object> out = new ArrayList<>();
        for (int i : sliceIndices(l.size(), lo, hi, step)) {
            out.add(l.get(i));
        }
        return out;
    }

    public static String str_slice(String s, Long lo, Long hi, Long step) {
        StringBuilder sb = new StringBuilder();
        for (int i : sliceIndices(s.length(), lo, hi, step)) {
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Itération
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public static Iterator<Object> iter(Object v) {
        if (v instanceof List) {
            return new ArrayList<>((List<Object>) v).iterator();
        }
        if (v instanceof Set) {
            return new ArrayList<>((Set<Object>) v).iterator();
        }
        if (v instanceof Map) {
            return new ArrayList<>(((Map<Object, Object>) v).keySet()).iterator();
        }
        if (v instanceof String) {
            String s = (String) v;
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < s.length(); i++) {
                out.add(String.valueOf(s.charAt(i)));
            }
            return out.iterator();
        }
        if (v instanceof Iterable) {
            List<Object> out = new ArrayList<>();
            for (Object o : (Iterable<Object>) v) {
                out.add(o);
            }
            return out.iterator();
        }
        throw rt("valeur non itérable : " + str(v));
    }

    // ------------------------------------------------------------------
    // Méthodes de str
    // ------------------------------------------------------------------

    public static long str_length(String s) {
        return s.length();
    }

    public static String str_upper(String s) {
        return s.toUpperCase();
    }

    public static String str_lower(String s) {
        return s.toLowerCase();
    }

    public static String str_strip(String s) {
        return s.strip();
    }

    public static List<Object> str_split(String s, String sep) {
        if (sep.isEmpty()) {
            throw rt("séparateur vide");
        }
        List<Object> out = new ArrayList<>();
        for (String p : s.split(java.util.regex.Pattern.quote(sep), -1)) {
            out.add(p);
        }
        return out;
    }

    public static String str_join(String sep, List<Object> items) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object o : items) {
            sb.append(first ? "" : sep).append(str(o));
            first = false;
        }
        return sb.toString();
    }

    public static boolean str_startswith(String s, String p) {
        return s.startsWith(p);
    }

    public static boolean str_endswith(String s, String p) {
        return s.endsWith(p);
    }

    public static boolean str_contains(String s, String p) {
        return s.contains(p);
    }

    public static String str_replace(String s, String a, String b) {
        return s.replace(a, b);
    }

    public static List<Object> str_lines(String s) {
        List<Object> out = new ArrayList<>();
        s.lines().forEach(out::add);
        return out;
    }

    // ------------------------------------------------------------------
    // Méthodes de list, map, set
    // ------------------------------------------------------------------

    public static void list_add(List<Object> l, Object x) {
        l.add(x);
    }

    public static boolean list_contains(List<Object> l, Object x) {
        return contains(l, x);
    }

    public static Object list_pop(List<Object> l) {
        if (l.isEmpty()) {
            throw rt("pop() sur une liste vide");
        }
        return l.remove(l.size() - 1);
    }

    public static void list_clear(List<Object> l) {
        l.clear();
    }

    public static void list_extend(List<Object> l, List<Object> other) {
        l.addAll(other);
    }

    public static List<Object> map_items(Map<Object, Object> m) {
        List<Object> out = new ArrayList<>();
        for (Map.Entry<Object, Object> e : m.entrySet()) {
            out.add(new Entry(e.getKey(), e.getValue()));
        }
        return out;
    }

    public static List<Object> map_keys(Map<Object, Object> m) {
        return new ArrayList<>(m.keySet());
    }

    public static List<Object> map_values(Map<Object, Object> m) {
        return new ArrayList<>(m.values());
    }

    public static Object map_get(Map<Object, Object> m, Object key) {
        return m.get(key);
    }

    public static boolean map_contains(Map<Object, Object> m, Object key) {
        return m.containsKey(key);
    }

    public static void map_remove(Map<Object, Object> m, Object key) {
        m.remove(key);
    }

    public static void set_add(Set<Object> s, Object x) {
        s.add(x);
    }

    public static boolean set_contains(Set<Object> s, Object x) {
        return s.contains(x);
    }

    public static void set_remove(Set<Object> s, Object x) {
        s.remove(x);
    }

    // ------------------------------------------------------------------
    // Fonctions prédéfinies
    // ------------------------------------------------------------------

    public static long len(Object v) {
        if (v instanceof String) {
            return ((String) v).length();
        }
        if (v instanceof Collection) {
            return ((Collection<?>) v).size();
        }
        if (v instanceof Map) {
            return ((Map<?, ?>) v).size();
        }
        throw rt("len() non applicable à " + str(v));
    }

    private static List<Object> range(long lo, long hi, long step) {
        if (step == 0) {
            throw rt("le pas de range() ne peut pas être 0");
        }
        List<Object> out = new ArrayList<>();
        for (long i = lo; step > 0 ? i < hi : i > hi; i += step) {
            out.add(i);
            if (out.size() > 5_000_000) {
                throw rt("range() trop grand");
            }
        }
        return out;
    }

    public static List<Object> range1(long hi) {
        return range(0, hi, 1);
    }

    public static List<Object> range2(long lo, long hi) {
        return range(lo, hi, 1);
    }

    public static List<Object> range3(long lo, long hi, long step) {
        return range(lo, hi, step);
    }

    public static long sumInt(List<Object> l) {
        long s = 0;
        for (Object o : l) {
            s = add(s, ((Number) o).longValue());
        }
        return s;
    }

    public static double sumFloat(List<Object> l) {
        double s = 0;
        for (Object o : l) {
            s += ((Number) o).doubleValue();
        }
        return s;
    }

    public static long absInt(long v) {
        return Math.abs(v);
    }

    public static double absFloat(double v) {
        return Math.abs(v);
    }

    public static long minInt(long a, long b) {
        return a <= b ? a : b;
    }

    public static double minFloat(double a, double b) {
        return a <= b ? a : b;
    }

    public static long maxInt(long a, long b) {
        return a >= b ? a : b;
    }

    public static double maxFloat(double a, double b) {
        return a >= b ? a : b;
    }

    public static List<Object> sorted1(List<Object> l) {
        List<Object> out = new ArrayList<>(l);
        out.sort(Rt::cmp);
        return out;
    }

    /** {@code sorted(items, key=f)} : tri stable sur la clé calculée une fois par élément. */
    public static List<Object> sorted2(List<Object> l, Fn key) {
        List<Object[]> decorated = new ArrayList<>();
        for (Object o : l) {
            decorated.add(new Object[] {key.call(new Object[] {o}), o});
        }
        decorated.sort((x, y) -> cmp(x[0], y[0]));
        List<Object> out = new ArrayList<>();
        for (Object[] d : decorated) {
            out.add(d[1]);
        }
        return out;
    }

    public static List<Object> filter(Fn pred, List<Object> l) {
        List<Object> out = new ArrayList<>();
        for (Object o : l) {
            if (Boolean.TRUE.equals(pred.call(new Object[] {o}))) {
                out.add(o);
            }
        }
        return out;
    }

    public static List<Object> enumerate(List<Object> l) {
        List<Object> out = new ArrayList<>();
        long i = 0;
        for (Object o : l) {
            out.add(new Entry(i++, o));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Conversions
    // ------------------------------------------------------------------

    public static long toInt(Object v) {
        if (v instanceof Long) {
            return (Long) v;
        }
        if (v instanceof Double) {
            double d = (Double) v;
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw valueError("conversion impossible en int : " + str(v));
            }
            return (long) d;
        }
        if (v instanceof Boolean) {
            return ((Boolean) v) ? 1L : 0L;
        }
        try {
            return Long.parseLong(((String) v).strip());
        } catch (NumberFormatException e) {
            throw valueError("conversion impossible en int : " + repr(v, 1));
        }
    }

    public static double toFloat(Object v) {
        if (v instanceof Double) {
            return (Double) v;
        }
        if (v instanceof Long) {
            return (double) (Long) v;
        }
        try {
            return Double.parseDouble(((String) v).strip());
        } catch (NumberFormatException | ClassCastException e) {
            throw valueError("conversion impossible en float : " + repr(v, 1));
        }
    }

    public static boolean toBool(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof Long) {
            return (Long) v != 0;
        }
        if (v instanceof Double) {
            return (Double) v != 0;
        }
        if (v instanceof String) {
            return !((String) v).isEmpty();
        }
        if (v instanceof Collection) {
            return !((Collection<?>) v).isEmpty();
        }
        if (v instanceof Map) {
            return !((Map<?, ?>) v).isEmpty();
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Interopérabilité Java : conversions aux frontières
    // ------------------------------------------------------------------

    public static int toJavaInt(long v) {
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            throw rt("entier hors des limites d'un int Java : " + v);
        }
        return (int) v;
    }

    public static short toJavaShort(long v) {
        if (v < Short.MIN_VALUE || v > Short.MAX_VALUE) {
            throw rt("entier hors des limites d'un short Java : " + v);
        }
        return (short) v;
    }

    public static byte toJavaByte(long v) {
        if (v < Byte.MIN_VALUE || v > Byte.MAX_VALUE) {
            throw rt("entier hors des limites d'un byte Java : " + v);
        }
        return (byte) v;
    }

    public static char toJavaChar(String s) {
        if (s.length() != 1) {
            throw rt("un caractère Java attend une chaîne de longueur 1, trouvé " + repr(s, 1));
        }
        return s.charAt(0);
    }

    /** Résultat Java (Integer, Long, Short, Byte ou élément de collection) vers un {@code int} Dystos. */
    public static long javaToLong(Object o) {
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        if (o == null) {
            throw rt("valeur Java nulle là où un int est attendu");
        }
        throw rt("valeur Java inattendue (int attendu) : " + str(o));
    }

    public static double javaToDouble(Object o) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        if (o == null) {
            throw rt("valeur Java nulle là où un float est attendu");
        }
        throw rt("valeur Java inattendue (float attendu) : " + str(o));
    }

    public static boolean javaToBool(Object o) {
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        if (o == null) {
            throw rt("valeur Java nulle là où un bool est attendu");
        }
        throw rt("valeur Java inattendue (bool attendu) : " + str(o));
    }

    public static String javaCharToString(Object o) {
        if (o instanceof Character) {
            return String.valueOf((char) (Character) o);
        }
        if (o == null) {
            throw rt("valeur Java nulle là où un caractère est attendu");
        }
        return str(o);
    }

    // ------------------------------------------------------------------
    // Modules io et concurrent
    // ------------------------------------------------------------------

    public static DFile io_open(String path) {
        return io_open(path, "r");
    }

    /** Ouvre un fichier texte sous le répertoire courant (jamais en dehors, comme l'interpréteur). */
    public static DFile io_open(String path, String mode) {
        if (!mode.equals("r") && !mode.equals("w") && !mode.equals("a")) {
            throw rt("mode d'ouverture inconnu : '" + mode + "' (r, w ou a)");
        }
        java.nio.file.Path base = java.nio.file.Path.of("").toAbsolutePath().normalize();
        java.nio.file.Path resolved = base.resolve(path).normalize();
        if (!resolved.startsWith(base)) {
            throw rt("chemin hors du répertoire de travail : " + path);
        }
        if (mode.equals("r") && !java.nio.file.Files.isRegularFile(resolved)) {
            throw rt("fichier introuvable : " + path);
        }
        return new DFile(resolved, mode);
    }

    public static void File_write(DFile f, String s) {
        f.write(s);
    }

    public static String File_read(DFile f) {
        return f.read();
    }

    public static List<Object> File_lines(DFile f) {
        return f.lines();
    }

    public static void File_close(DFile f) {
        f.close();
    }

    public static DTask concurrent_run(Fn task) {
        return new DTask(task);
    }

    public static Object Task_await(DTask t) {
        return t.await();
    }

    /** Fin d'un bloc {@code with} : ferme l'objet. */
    public static void close(Object o) {
        if (o instanceof AutoCloseable) {
            try {
                ((AutoCloseable) o).close();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw rt("erreur à la fermeture : " + e.getMessage());
            }
        }
    }

    /** Utilisé par les littéraux de map pour conserver l'ordre d'insertion. */
    public static Map<Object, Object> newMap() {
        return new LinkedHashMap<>();
    }
}
