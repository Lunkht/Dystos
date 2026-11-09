package com.vulsoft.dystos.jvm;

import java.util.List;
import java.util.function.Consumer;

/** Parcours générique de l'AST (les nœuds sont des enregistrements du paquet {@code com.vulsoft.dystos.ast}). */
final class AstWalk {

    private AstWalk() {
    }

    /** Visite {@code node} et tous ses descendants (listes et enregistrements de l'AST). */
    static void forEach(Object node, Consumer<Object> visit) {
        if (node == null) {
            return;
        }
        if (node instanceof List<?> l) {
            for (Object o : l) {
                forEach(o, visit);
            }
            return;
        }
        if (node.getClass().isRecord() && node.getClass().getName().startsWith("com.vulsoft.dystos.ast")) {
            visit.accept(node);
            for (var rc : node.getClass().getRecordComponents()) {
                try {
                    forEach(rc.getAccessor().invoke(node), visit);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }
}
