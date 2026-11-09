package com.vulsoft.dystos.lexer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Colorateur syntaxique tolérant : fonctionne sur du code incomplet ou erroné (contrairement à
 * {@link Lexer}, qui s'arrête à la première erreur), et conserve les commentaires.
 *
 * <p>Retourne des {@link Span} triés, sans chevauchement, en indices de caractères (UTF-16, donc
 * directement utilisables avec {@code Spannable} sur Android). Les identifiants ordinaires n'ont pas
 * de span.
 *
 * <p>Les listes {@link #KEYWORDS}, {@link #TYPES}, {@link #BUILTINS} et {@link #OPERATORS} sont la source
 * unique des grammaires TextMate et Prism générées par {@code tools/GenerateGrammar.java}.
 */
public final class Highlighter {

    public enum Kind {
        KEYWORD, CONSTANT, TYPE, FUNCTION, BUILTIN, SELF, NUMBER, STRING, ESCAPE, COMMENT, DECORATOR,
        OPERATOR, PUNCTUATION
    }

    public record Span(int start, int end, Kind kind) {
    }

    /** Mots-clés (sans true/false/null), dans l'ordre de {@link TokenType}. */
    public static final Set<String> KEYWORDS;
    public static final Set<String> CONSTANTS = set("true", "false", "null");
    public static final Set<String> TYPES = set("int", "float", "bool", "str", "list", "map", "set", "unit", "any",
            "Entry", "Exception", "ValueError", "RuntimeError");
    public static final Set<String> BUILTINS = set("print", "len", "range", "sum", "sorted", "filter", "enumerate",
            "abs", "min", "max");
    /** Opérateurs, du plus long au plus court. */
    public static final List<String> OPERATORS = List.of("**", "->", "?.", "?:", "==", "!=", "<=", ">=", "+=", "-=",
            "*=", "/=", "%=", "+", "-", "*", "/", "%", "<", ">", "=", "?", "|");
    public static final String PUNCTUATION = "()[]{},:.";

    static {
        Set<String> kw = new LinkedHashSet<>();
        for (TokenType t : TokenType.values()) {
            if (t.ordinal() >= TokenType.DEF.ordinal() && t.ordinal() <= TokenType.IS.ordinal()) {
                kw.add(t.name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        kw.removeAll(CONSTANTS);
        KEYWORDS = Collections.unmodifiableSet(kw);
    }

    private Highlighter() {
    }

    private static Set<String> set(String... s) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(s)));
    }

    public static List<Span> highlight(String text) {
        List<Span> out = new ArrayList<>();
        scan(text, 0, text.length(), out);
        return out;
    }

    // ------------------------------------------------------------------

    private static void scan(String t, int from, int to, List<Span> out) {
        int i = from;
        String prevWord = "";
        while (i < to) {
            char c = t.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '#') {
                int e = lineEnd(t, i, to);
                out.add(new Span(i, e, Kind.COMMENT));
                i = e;
            } else if (c == '@' && i + 1 < to && isIdentStart(t.charAt(i + 1))) {
                int e = i + 1;
                while (e < to && (isIdentPart(t.charAt(e)) || t.charAt(e) == '.')) {
                    e++;
                }
                out.add(new Span(i, e, Kind.DECORATOR));
                i = e;
            } else if (c == '"' || c == '\'' || ((c == 'f' || c == 'F') && i + 1 < to
                    && (t.charAt(i + 1) == '"' || t.charAt(i + 1) == '\''))) {
                i = string(t, i, to, out);
            } else if (isDigit(c)) {
                int e = number(t, i, to);
                out.add(new Span(i, e, Kind.NUMBER));
                i = e;
            } else if (isIdentStart(c)) {
                int e = i;
                while (e < to && isIdentPart(t.charAt(e))) {
                    e++;
                }
                String w = t.substring(i, e);
                Kind k = classify(w, prevWord, t, e, to);
                if (k != null) {
                    out.add(new Span(i, e, k));
                }
                prevWord = w;
                i = e;
            } else {
                int e = operator(t, i, to);
                if (e > i) {
                    out.add(new Span(i, e, Kind.OPERATOR));
                    i = e;
                } else if (PUNCTUATION.indexOf(c) >= 0) {
                    out.add(new Span(i, i + 1, Kind.PUNCTUATION));
                    i++;
                } else {
                    i++;
                }
            }
        }
    }

    private static Kind classify(String w, String prev, String t, int end, int to) {
        if (KEYWORDS.contains(w)) {
            return Kind.KEYWORD;
        }
        if (CONSTANTS.contains(w)) {
            return Kind.CONSTANT;
        }
        if (w.equals("self")) {
            return Kind.SELF;
        }
        if (prev.equals("def")) {
            return Kind.FUNCTION;
        }
        if (prev.equals("class") || prev.equals("interface") || prev.equals("enum") || TYPES.contains(w)) {
            return Kind.TYPE;
        }
        boolean call = end < to && t.charAt(end) == '(';
        if (call && BUILTINS.contains(w)) {
            return Kind.BUILTIN;
        }
        char first = w.charAt(0);
        if (Character.isUpperCase(first)) {
            boolean allCaps = w.length() > 1 && w.equals(w.toUpperCase());
            return allCaps ? Kind.CONSTANT : Kind.TYPE;
        }
        return call ? Kind.FUNCTION : null;
    }

    private static int number(String t, int i, int to) {
        int e = i;
        if (t.charAt(i) == '0' && i + 1 < to && "xXbB".indexOf(t.charAt(i + 1)) >= 0) {
            e = i + 2;
            while (e < to && (Character.digit(t.charAt(e), 16) >= 0 || t.charAt(e) == '_')) {
                e++;
            }
            return e;
        }
        while (e < to && (isDigit(t.charAt(e)) || t.charAt(e) == '_')) {
            e++;
        }
        if (e + 1 < to && t.charAt(e) == '.' && isDigit(t.charAt(e + 1))) {
            e++;
            while (e < to && (isDigit(t.charAt(e)) || t.charAt(e) == '_')) {
                e++;
            }
        }
        if (e < to && (t.charAt(e) == 'e' || t.charAt(e) == 'E')) {
            int k = e + 1;
            if (k < to && (t.charAt(k) == '+' || t.charAt(k) == '-')) {
                k++;
            }
            if (k < to && isDigit(t.charAt(k))) {
                while (k < to && isDigit(t.charAt(k))) {
                    k++;
                }
                e = k;
            }
        }
        return e;
    }

    private static int operator(String t, int i, int to) {
        for (String op : OPERATORS) {
            if (t.startsWith(op, i) && i + op.length() <= to) {
                return i + op.length();
            }
        }
        return i;
    }

    // ---- chaînes ----------------------------------------------------------

    /** Retourne l'indice suivant la chaîne. */
    private static int string(String t, int start, int to, List<Span> out) {
        int i = start;
        boolean formatted = t.charAt(i) == 'f' || t.charAt(i) == 'F';
        if (formatted) {
            i++;
        }
        char q = t.charAt(i);
        boolean triple = i + 2 < to && t.charAt(i + 1) == q && t.charAt(i + 2) == q;
        i += triple ? 3 : 1;
        int seg = start;
        while (i < to) {
            char c = t.charAt(i);
            if (c == '\n' && !triple) {
                break;
            }
            if (c == q && (!triple || (i + 2 < to + 0 && t.startsWith("" + q + q + q, i)))) {
                i += triple ? 3 : 1;
                flush(out, seg, i, Kind.STRING);
                return i;
            }
            if (c == '\\' && i + 1 < to) {
                flush(out, seg, i, Kind.STRING);
                int e = escapeEnd(t, i, to);
                out.add(new Span(i, e, Kind.ESCAPE));
                i = e;
                seg = i;
                continue;
            }
            if (formatted && c == '{') {
                if (i + 1 < to && t.charAt(i + 1) == '{') {
                    i += 2;
                    continue;
                }
                int close = exprEnd(t, i + 1, to, q);
                flush(out, seg, i, Kind.STRING);
                out.add(new Span(i, i + 1, Kind.PUNCTUATION));
                int specAt = formatSeparator(t, i + 1, close);
                scan(t, i + 1, specAt < 0 ? close : specAt, out);
                if (specAt >= 0) {
                    out.add(new Span(specAt, specAt + 1, Kind.OPERATOR));
                    flush(out, specAt + 1, close, Kind.STRING);
                }
                if (close < to && t.charAt(close) == '}') {
                    out.add(new Span(close, close + 1, Kind.PUNCTUATION));
                    i = close + 1;
                } else {
                    i = close;
                }
                seg = i;
                continue;
            }
            i++;
        }
        flush(out, seg, i, Kind.STRING);
        return i;
    }

    private static void flush(List<Span> out, int from, int to, Kind k) {
        if (to > from) {
            out.add(new Span(from, to, k));
        }
    }

    private static int escapeEnd(String t, int i, int to) {
        if (i + 1 < to && t.charAt(i + 1) == 'u') {
            int e = i + 2;
            while (e < to && e < i + 6 && Character.digit(t.charAt(e), 16) >= 0) {
                e++;
            }
            return e;
        }
        return Math.min(i + 2, to);
    }

    /** Index de l'accolade fermante de l'expression (ou fin de ligne / de texte si absente). */
    private static int exprEnd(String t, int from, int to, char outerQuote) {
        int depth = 1;
        int i = from;
        while (i < to) {
            char c = t.charAt(i);
            if (c == '\n') {
                return i;
            }
            if ((c == '"' || c == '\'') && c != outerQuote) {
                int j = i + 1;
                while (j < to && t.charAt(j) != c && t.charAt(j) != '\n') {
                    j++;
                }
                i = j < to && t.charAt(j) == c ? j + 1 : j;
                continue;
            }
            if (c == outerQuote) {
                return i;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        return to;
    }

    /** Index du « : » de format au niveau 0 (hors crochets, chaînes et « ?: »), ou -1. */
    private static int formatSeparator(String t, int from, int to) {
        int depth = 0;
        for (int i = from; i < to; i++) {
            char c = t.charAt(i);
            if (c == '\'' || c == '"') {
                int j = i + 1;
                while (j < to && t.charAt(j) != c) {
                    j++;
                }
                i = j;
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ':' && depth == 0 && !(i > from && t.charAt(i - 1) == '?')) {
                return i;
            }
        }
        return -1;
    }

    // ---- utilitaires --------------------------------------------------------

    private static int lineEnd(String t, int i, int to) {
        int e = i;
        while (e < to && t.charAt(e) != '\n') {
            e++;
        }
        return e;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
