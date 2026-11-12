package dev.dystos.app.language;

import java.util.Collections;
import java.util.List;

/** Conversion d'une valeur itérable pour la boucle {@code for}. */
final class IterableSupport {

    private IterableSupport() {
    }

    /** Éléments à parcourir, dans l'ordre. */
    static List<Object> iterate(Object value, int line) {
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> items = (List<Object>) value;
            return items;
        }
        if (value instanceof String) {
            String text = (String) value;
            List<Object> characters = new java.util.ArrayList<>(text.length());
            for (int i = 0; i < text.length(); i++) {
                characters.add(String.valueOf(text.charAt(i)));
            }
            return characters;
        }
        throw new DystosException(
                "impossible de parcourir une valeur de type '" + Types.nameOf(value) + "'",
                line);
    }

    static List<Object> empty() {
        return Collections.emptyList();
    }
}