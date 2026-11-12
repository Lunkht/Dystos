package dev.dystos.app.domain.model;

/** Comparaison de la sortie produite par la sortie attendue. */
public final class OutputComparator {

    private OutputComparator() {
    }

    /**
     * Compare deux sorties en ignorant les écarts de espaces et de lignes
     * vides.
     *
     * L'apprenant n'a pas à deviner où sont les espaces : une différence de
     * formatage n'est pas une erreur de logique. En revanche l'ordre des
     * lignes compte.
     */
    public static boolean matches(String actual, String expected) {
        String[] actualLines = normalise(actual);
        String[] expectedLines = normalise(expected);
        if (actualLines.length != expectedLines.length) {
            return false;
        }
        for (int i = 0; i < actualLines.length; i++) {
            if (!actualLines[i].equals(expectedLines[i])) {
                return false;
            }
        }
        return true;
    }

    /** Lignes comparables : sans espaces de bord, sans ligne vide finale. */
    private static String[] normalise(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        int end = lines.length;
        while (end > 0 && lines[end - 1].trim().isEmpty()) {
            end--;
        }
        String[] result = new String[end];
        for (int i = 0; i < end; i++) {
            result[i] = lines[i].trim();
        }
        return result;
    }
}