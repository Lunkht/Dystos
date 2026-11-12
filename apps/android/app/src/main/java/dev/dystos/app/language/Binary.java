package dev.dystos.app.language;

/**
 * Opérations binaires.
 *
 * La promotion {@code int} vers {@code float} suit les usages simples : la
 * division de deux entiers donne un entier si elle tombe juste, sinon un
 * flottant. C'est explicite dans les cours plutôt qu'implicite dans le code.
 */
final class Binary {

    private Binary() {
    }

    static Object apply(String operator, Object left, Object right, int line) {
        switch (operator) {
            case "+":
                return add(left, right, line);
            case "-":
            case "*":
            case "/":
            case "%":
                return arithmetic(operator, left, right, line);
            case "==":
                return equalValues(left, right);
            case "!=":
                return !equalValues(left, right);
            case "<":
            case "<=":
            case ">":
            case ">=":
                return compare(operator, left, right, line);
            default:
                throw new DystosException("opérateur inconnu : '" + operator + "'", line);
        }
    }

    private static Object add(Object left, Object right, int line) {
        if (left instanceof String || right instanceof String) {
            return Types.concat(left, right, line);
        }
        return arithmetic("+", left, right, line);
    }

    private static Object arithmetic(String operator, Object left, Object right, int line) {
        if (!isNumeric(left) || !isNumeric(right)) {
            throw new DystosException(
                    "opération '" + operator + "' impossible entre '"
                            + Types.nameOf(left) + "' et '" + Types.nameOf(right) + "'", line);
        }

        if (left instanceof Long && right instanceof Long) {
            return longArithmetic(operator, (Long) left, (Long) right, line);
        }

        double a = toDouble(left);
        double b = toDouble(right);
        switch (operator) {
            case "+":
                return a + b;
            case "-":
                return a - b;
            case "*":
                return a * b;
            case "/":
                if (b == 0.0) {
                    throw new DystosException("division par zéro", line);
                }
                double quotient = a / b;
                // 7 / 2 vaut 3.5, mais 6 / 2 vaut 3 : le type suit le résultat.
                return quotient == Math.rint(quotient) && Math.abs(quotient) < 1e15
                        ? (Object) (long) quotient
                        : (Object) quotient;
            case "%":
                if (b == 0.0) {
                    throw new DystosException("reste de division par zéro", line);
                }
                double rest = a % b;
                return rest == Math.rint(rest) ? (Object) (long) rest : (Object) rest;
            default:
                throw new DystosException("opérateur inconnu : '" + operator + "'", line);
        }
    }

    private static Object longArithmetic(String operator, long a, long b, int line) {
        switch (operator) {
            case "+":
                return a + b;
            case "-":
                return a - b;
            case "*":
                return a * b;
            case "/":
                if (b == 0) {
                    throw new DystosException("division par zéro", line);
                }
                // 6 / 2 vaut 3, mais 7 / 2 vaut 3.5 : le type suit le résultat.
                return a % b == 0 ? (Object) (a / b) : (Object) ((double) a / b);
            case "%":
                if (b == 0) {
                    throw new DystosException("reste de division par zéro", line);
                }
                return a % b;
            default:
                throw new DystosException("opérateur inconnu : '" + operator + "'", line);
        }
    }

    /** Comparaison de valeurs, avec un ordre total entre types numériques. */
    static int compare(Object left, Object right, int line) {
        if (isNumeric(left) && isNumeric(right)) {
            if (left instanceof Long && right instanceof Long) {
                return Long.compare((Long) left, (Long) right);
            }
            return Double.compare(toDouble(left), toDouble(right));
        }
        if (left instanceof String && right instanceof String) {
            return ((String) left).compareTo((String) right);
        }
        if (left instanceof Boolean && right instanceof Boolean) {
            return Boolean.compare((Boolean) left, (Boolean) right);
        }
        throw new DystosException(
                "comparaison impossible entre '" + Types.nameOf(left) + "' et '"
                        + Types.nameOf(right) + "'", line);
    }

    private static Object compare(String operator, Object left, Object right, int line) {
        int result = compare(left, right, line);
        switch (operator) {
            case "<":
                return result < 0;
            case "<=":
                return result <= 0;
            case ">":
                return result > 0;
            case ">=":
                return result >= 0;
            default:
                throw new DystosException("opérateur inconnu : '" + operator + "'", line);
        }
    }

    private static boolean equalValues(Object left, Object right) {
        if (isNumeric(left) && isNumeric(right)) {
            return toDouble(left) == toDouble(right);
        }
        if (left == null || right == null) {
            return left == right;
        }
        return left.equals(right);
    }

    private static boolean isNumeric(Object value) {
        return value instanceof Long || value instanceof Double;
    }

    private static double toDouble(Object value) {
        return value instanceof Long ? (double) (Long) value : (Double) value;
    }
}