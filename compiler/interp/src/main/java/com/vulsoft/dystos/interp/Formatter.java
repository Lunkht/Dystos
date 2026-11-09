package com.vulsoft.dystos.interp;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Formats d'f-string : {@code {x:.2f}}, {@code {n:05d}}, {@code {s:>10}}, {@code {n:,}}, {@code {r:.1%}}. */
final class Formatter {

    static final Pattern SPEC = Pattern.compile("^(?:(.)?([<>^]))?([+\\- ])?(0)?(\\d+)?(,)?(?:\\.(\\d+))?([a-zA-Z%])?$");

    private Formatter() {
    }

    static String format(Interpreter in, Object v, String spec) {
        if (spec == null || spec.isEmpty()) {
            return in.str(v);
        }
        Matcher m = SPEC.matcher(spec);
        if (!m.matches()) {
            throw in.rt("format invalide : '" + spec + "'");
        }
        String fill = m.group(1) == null ? " " : m.group(1);
        String align = m.group(2);
        String sign = m.group(3) == null ? "" : m.group(3);
        boolean zero = m.group(4) != null;
        int width = m.group(5) == null ? 0 : Integer.parseInt(m.group(5));
        boolean group = m.group(6) != null;
        Integer prec = m.group(7) == null ? null : Integer.parseInt(m.group(7));
        String type = m.group(8);
        boolean numeric = v instanceof Long || v instanceof Double;
        if (type == null) {
            type = v instanceof Long ? "d" : v instanceof Double ? (prec != null ? "f" : "") : "s";
        }

        boolean negative = false;
        String body;
        switch (type) {
            case "d": {
                if (!(v instanceof Long l)) {
                    throw in.rt("le format 'd' attend un entier, trouvé " + in.str(v));
                }
                negative = l < 0;
                body = Long.toString(l).replace("-", "");
                if (group) {
                    body = grouped(body);
                }
                break;
            }
            case "f":
            case "e":
            case "%": {
                double d = number(in, v);
                negative = d < 0;
                int p = prec == null ? 6 : prec;
                double a = Math.abs(d) * (type.equals("%") ? 100 : 1);
                body = String.format(Locale.ROOT, "%." + p + (type.equals("e") ? "e" : "f"), a);
                if (group && !type.equals("e")) {
                    int dot = body.indexOf('.');
                    body = dot < 0 ? grouped(body) : grouped(body.substring(0, dot)) + body.substring(dot);
                }
                if (type.equals("%")) {
                    body += "%";
                }
                break;
            }
            case "": {
                double d = number(in, v);
                negative = d < 0;
                body = Values.formatDouble(Math.abs(d));
                break;
            }
            case "x":
            case "X":
            case "o":
            case "b": {
                if (!(v instanceof Long l)) {
                    throw in.rt("le format '" + type + "' attend un entier");
                }
                negative = l < 0;
                long a = Math.abs(l);
                body = type.equals("b") ? Long.toBinaryString(a) : type.equals("o") ? Long.toOctalString(a)
                        : Long.toHexString(a);
                if (type.equals("X")) {
                    body = body.toUpperCase(Locale.ROOT);
                }
                break;
            }
            case "s": {
                body = in.str(v);
                if (prec != null && prec < body.length()) {
                    body = body.substring(0, prec);
                }
                break;
            }
            default:
                throw in.rt("type de format inconnu : '" + type + "'");
        }

        String signText = negative ? "-" : sign.equals("+") ? "+" : sign.equals(" ") ? " " : "";
        int pad = width - signText.length() - body.length();
        if (pad <= 0) {
            return signText + body;
        }
        if (zero && numeric && align == null) {
            return signText + "0".repeat(pad) + body;
        }
        String a = align != null ? align : numeric ? ">" : "<";
        String full = signText + body;
        switch (a) {
            case ">":
                return fill.repeat(pad) + full;
            case "^": {
                int left = pad / 2;
                return fill.repeat(left) + full + fill.repeat(pad - left);
            }
            default:
                return full + fill.repeat(pad);
        }
    }

    private static double number(Interpreter in, Object v) {
        if (v instanceof Long l) {
            return l;
        }
        if (v instanceof Double d) {
            return d;
        }
        throw in.rt("format numérique appliqué à " + in.str(v));
    }

    private static String grouped(String digits) {
        StringBuilder sb = new StringBuilder();
        int n = digits.length();
        for (int i = 0; i < n; i++) {
            if (i > 0 && (n - i) % 3 == 0) {
                sb.append(',');
            }
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }
}
