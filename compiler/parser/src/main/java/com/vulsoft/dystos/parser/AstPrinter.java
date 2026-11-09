package com.vulsoft.dystos.parser;

import java.lang.reflect.RecordComponent;
import java.util.List;

/** Affiche un AST sous forme d'arbre indenté (outil de débogage). */
public final class AstPrinter {

    private AstPrinter() {
    }

    public static String print(Object node) {
        StringBuilder sb = new StringBuilder();
        header(node, sb);
        sb.append('\n');
        fields(node, 1, sb);
        return sb.toString();
    }

    private static void header(Object node, StringBuilder sb) {
        sb.append(node.getClass().getSimpleName());
        try {
            Object pos = node.getClass().getMethod("pos").invoke(node);
            sb.append(" @").append(pos);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // pas de position (ex. FText)
        }
    }

    private static void fields(Object node, int indent, StringBuilder sb) {
        for (RecordComponent rc : node.getClass().getRecordComponents()) {
            if (rc.getName().equals("pos")) {
                continue;
            }
            Object value;
            try {
                value = rc.getAccessor().invoke(node);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            write(rc.getName(), value, indent, sb);
        }
    }

    private static void write(String label, Object v, int indent, StringBuilder sb) {
        String pad = "  ".repeat(indent);
        if (v == null || Boolean.FALSE.equals(v)) {
            return;
        }
        if (v instanceof List<?> list) {
            if (list.isEmpty()) {
                return;
            }
            sb.append(pad).append(label).append(":\n");
            for (Object item : list) {
                sb.append(pad).append("  - ");
                if (item.getClass().isRecord()) {
                    header(item, sb);
                    sb.append('\n');
                    fields(item, indent + 2, sb);
                } else {
                    sb.append(item).append('\n');
                }
            }
        } else if (v.getClass().isRecord()) {
            sb.append(pad).append(label).append(": ");
            header(v, sb);
            sb.append('\n');
            fields(v, indent + 1, sb);
        } else {
            sb.append(pad).append(label).append(": ").append(v).append('\n');
        }
    }
}
