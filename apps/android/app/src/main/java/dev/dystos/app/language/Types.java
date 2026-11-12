package dev.dystos.app.language;

/** Vérifications de type, avec des messages utilisables par l'apprenant. */
final class Types {

    private Types() {
    }

    /** Nom du type d'une valeur, pour les messages d'erreur. */
    static String nameOf(Object value) {
        if (value instanceof Long) {
            return "int";
        }
        if (value instanceof Double) {
            return "float";
        }
        if (value instanceof String) {
            return "str";
        }
        if (value instanceof Boolean) {
            return "bool";
        }
        if (value instanceof java.util.List) {
            return "list";
        }
        if (value instanceof FunctionValue) {
            return "fonction";
        }
        return "void";
    }

    /** Vrai si la valeur est compatible avec le type déclaré. */
    static boolean matches(Object value, String declaredType) {
        if (declaredType == null) {
            return true;
        }
        switch (declaredType) {
            case "int":
                return value instanceof Long;
            case "float":
                return value instanceof Long || value instanceof Double;
            case "str":
                return value instanceof String;
            case "bool":
                return value instanceof Boolean;
            case "void":
                return value == null;
            default:
                // Type non vérifié : list<...> et types composites.
                return true;
        }
    }

    /** Vérifie le type déclaré et lève une erreur explicite si besoin. */
    static void checkDeclaredType(Object value, String declaredType, int line) {
        if (matches(value, declaredType)) {
            return;
        }
        throw new DystosException(
                "type attendu '" + declaredType + "', obtenu '" + nameOf(value) + "'", line);
    }

    /** Interprète une valeur comme un booléen. */
    static boolean asBoolean(Object value, int line) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        throw new DystosException(
                "condition attendue de type 'bool', obtenu '" + nameOf(value) + "'", line);
    }

    /** Convertit une valeur en entier, sans perte. */
    static long toLong(Object value, int line) {
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Double) {
            double number = (Double) value;
            if (number == Math.rint(number)) {
                return (long) number;
            }
            throw new DystosException(
                    "'" + Display.render(value) + "' n'est pas un entier", line);
        }
        throw new DystosException(
                "entier attendu, obtenu '" + nameOf(value) + "'", line);
    }

    /** Négation unaire. */
    static Object negate(Object value, int line) {
        if (value instanceof Long) {
            return -(Long) value;
        }
        if (value instanceof Double) {
            return -(Double) value;
        }
        throw new DystosException(
                "négation impossible sur '" + nameOf(value) + "'", line);
    }

    /** Concaténation de chaînes : {@code "a" + "b"}. */
    static Object concat(Object left, Object right, int line) {
        if (left instanceof String || right instanceof String) {
            return Display.render(left) + Display.render(right);
        }
        throw new DystosException(
                "l'opérateur '+' sur '" + nameOf(left) + "' et '" + nameOf(right)
                        + "' n'est pas défini", line);
    }
}