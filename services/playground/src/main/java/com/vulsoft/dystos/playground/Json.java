package com.vulsoft.dystos.playground;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lecteur et écrivain JSON minimal (objets, tableaux, chaînes, nombres, booléens, null). */
final class Json {

    private static final int MAX_DEPTH = 32;

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    /** Analyse un document JSON. Objets : Map, tableaux : List, nombres : Long ou Double. */
    static Object parse(String text) {
        Json p = new Json(text);
        p.skip();
        Object v = p.value(0);
        p.skip();
        if (p.i != p.s.length()) {
            throw p.error("contenu inattendu après la fin du document");
        }
        return v;
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException(msg + " (position " + i + ")");
    }

    private void skip() {
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\n' || s.charAt(i) == '\r' || s.charAt(i) == '\t')) {
            i++;
        }
    }

    private Object value(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("imbrication trop profonde");
        }
        if (i >= s.length()) {
            throw error("fin de document inattendue");
        }
        char c = s.charAt(i);
        switch (c) {
            case '{': return object(depth);
            case '[': return array(depth);
            case '"': return string();
            case 't': return literal("true", Boolean.TRUE);
            case 'f': return literal("false", Boolean.FALSE);
            case 'n': return literal("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return number();
                }
                throw error("caractère inattendu '" + c + "'");
        }
    }

    private Object literal(String word, Object value) {
        if (!s.startsWith(word, i)) {
            throw error("littéral invalide");
        }
        i += word.length();
        return value;
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        skip();
        if (i < s.length() && s.charAt(i) == '}') {
            i++;
            return m;
        }
        while (true) {
            skip();
            if (i >= s.length() || s.charAt(i) != '"') {
                throw error("clé de chaîne attendue");
            }
            String key = string();
            skip();
            if (i >= s.length() || s.charAt(i) != ':') {
                throw error("':' attendu");
            }
            i++;
            skip();
            m.put(key, value(depth + 1));
            skip();
            if (i < s.length() && s.charAt(i) == ',') {
                i++;
                continue;
            }
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return m;
            }
            throw error("',' ou '}' attendu");
        }
    }

    private List<Object> array(int depth) {
        List<Object> l = new ArrayList<>();
        i++;
        skip();
        if (i < s.length() && s.charAt(i) == ']') {
            i++;
            return l;
        }
        while (true) {
            skip();
            l.add(value(depth + 1));
            skip();
            if (i < s.length() && s.charAt(i) == ',') {
                i++;
                continue;
            }
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return l;
            }
            throw error("',' ou ']' attendu");
        }
    }

    private String string() {
        StringBuilder sb = new StringBuilder();
        i++;
        while (true) {
            if (i >= s.length()) {
                throw error("chaîne non terminée");
            }
            char c = s.charAt(i++);
            if (c == '"') {
                return sb.toString();
            }
            if (c < 0x20) {
                throw error("caractère de contrôle dans une chaîne");
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (i >= s.length()) {
                throw error("échappement incomplet");
            }
            char e = s.charAt(i++);
            switch (e) {
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    if (i + 4 > s.length()) {
                        throw error("séquence \\u incomplète");
                    }
                    try {
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("séquence \\u invalide");
                    }
                    i += 4;
                    break;
                default:
                    throw error("échappement inconnu \\" + e);
            }
        }
    }

    private Object number() {
        int start = i;
        if (s.charAt(i) == '-') {
            i++;
        }
        boolean isDouble = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                i++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                isDouble = true;
                i++;
            } else {
                break;
            }
        }
        String t = s.substring(start, i);
        try {
            return isDouble ? (Object) Double.parseDouble(t) : (Object) Long.parseLong(t);
        } catch (NumberFormatException e) {
            throw error("nombre invalide '" + t + "'");
        }
    }

    // ------------------------------------------------------------------

    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }
}
