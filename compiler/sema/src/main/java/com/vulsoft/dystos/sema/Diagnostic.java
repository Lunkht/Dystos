package com.vulsoft.dystos.sema;

import java.util.List;

/**
 * Message du compilateur (erreur ou avertissement), sérialisable en JSON pour le site et l'application.
 *
 * @param file module concerné (null si un seul fichier est analysé)
 */
public record Diagnostic(Severity severity, String code, String message, int line, int column, String file) {

    public enum Severity { ERROR, WARNING }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"severity\":\"").append(severity == Severity.ERROR ? "error" : "warning").append('"');
        sb.append(",\"code\":\"").append(escape(code)).append('"');
        sb.append(",\"message\":\"").append(escape(message)).append('"');
        sb.append(",\"line\":").append(line);
        sb.append(",\"column\":").append(column);
        if (file != null) {
            sb.append(",\"file\":\"").append(escape(file)).append('"');
        }
        return sb.append('}').toString();
    }

    public static String toJson(List<Diagnostic> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(list.get(i).toJson());
        }
        return sb.append(']').toString();
    }

    @Override
    public String toString() {
        return "ligne " + line + ", colonne " + column + " [" + code + "] " + message;
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
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
        return sb.toString();
    }
}
