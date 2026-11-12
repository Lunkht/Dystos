package dev.dystos.app.language;

import java.util.List;

/** Rendu d'une valeur pour {@code print} et les chaînes formatées. */
final class Display {

    private Display() {
    }

    /**
     * Convertit une valeur en texte.
     *
     * Un flottant qui a une partie entière est rendu sans « .0 » : c'est le
     * comportement attendu dans les cours, où {@code 2 / 2} doit afficher
     * {@code 1}. Les flottants qui ont une vraie partie décimale gardent leur
     * point, y compris le zéro final.
     */
    static String render(Object value) {
        if (value == null) {
            return "void";
        }
        if (value instanceof Double) {
            return renderDouble((Double) value);
        }
        if (value instanceof Float) {
            return renderDouble(((Float) value).doubleValue());
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "true" : "false";
        }
        if (value instanceof List) {
            StringBuilder text = new StringBuilder("[");
            List<?> items = (List<?>) value;
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) {
                    text.append(", ");
                }
                text.append(render(items.get(i)));
            }
            return text.append(']').toString();
        }
        if (value instanceof FunctionValue) {
            return value.toString();
        }
        return String.valueOf(value);
    }

    /**
     * Un flottant garde toujours sa partie décimale : {@code 1.0} et non
     * {@code 1}, sinon {@code print(100.0 * 1.2)} afficherait un entier alors
     * que le type est {@code float}.
     *
     * Le bruit binaire de l'arithmétique flottante est arrondi à six décimales
     * : {@code 100.0 * 1.2} vaut 120.00000000000001 en IEEE 754, ce qui n'est
     * jamais ce que l'apprenant a écrit.
     */
    private static String renderDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return String.valueOf(value);
        }
        if (Math.abs(value) >= 1e15) {
            return String.valueOf(value);
        }
        double rounded = Math.round(value * 1e6) / 1e6;
        if (rounded == 0.0) {
            return "0.0";
        }
        if (rounded == Math.rint(rounded)) {
            return String.valueOf((long) rounded) + ".0";
        }
        return String.valueOf(rounded);
    }
}