package dev.dystos.app.language;

/**
 * Fonctions intégrées.
 *
 * Elles sont déclarées dans la portée racine sous forme de {@link FunctionValue}
 * à arguments nommés, ce qui évite un second chemin d'appel : c'est le même
 * chemin que pour une fonction écrite par l'apprenant.
 */
final class Builtins {

    private Builtins() {
    }

    /** Déclare les fonctions intégrées dans {@code scope}. */
    static void install(Scope scope) {
        for (String name : signatureOfNames()) {
            scope.declare(name, new FunctionValue(
                    declarationFor(name)), false, null);
        }
    }

    private static java.util.List<String> signatureOfNames() {
        return java.util.Arrays.asList("print", "range", "len", "str", "int", "float",
                "bool", "abs", "min", "max", "round");
    }

    /** Signature d'une fonction intégrée, ou null si le nom n'en est pas une. */
    static java.util.List<Parameter> signatureOf(String name) {
        if (!signatureOfNames().contains(name)) {
            return null;
        }
        return java.util.Arrays.asList(parametersFor(name));
    }

    static Object call(String name, java.util.List<Object> values, int line,
                       Interpreter interpreter) {
        switch (name) {
            case "print":
                return print(values, interpreter);
            case "range":
                return range(values, line);
            case "len":
                return length(values, line);
            case "str":
                return Display.render(argument(values, 0, name, line));
            case "int":
                return toInt(argument(values, 0, name, line), line);
            case "float":
                return toFloat(argument(values, 0, name, line), line);
            case "bool":
                return Types.asBoolean(argument(values, 0, name, line), line);
            case "abs":
                return absolute(argument(values, 0, name, line), line);
            case "min":
                return extremum(values, name, line, true);
            case "max":
                return extremum(values, name, line, false);
            case "round":
                return round(values, line);
            default:
                throw new DystosException("fonction inconnue : '" + name + "'", line);
        }
    }

    private static Object print(java.util.List<Object> values, Interpreter interpreter) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                line.append(' ');
            }
            line.append(Display.render(values.get(i)));
        }
        interpreter.print(line.toString());
        return null;
    }

    private static Object range(java.util.List<Object> values, int line) {
        long start = 0;
        long end;
        long step = 1;

        if (values.size() == 1) {
            end = Types.toLong(values.get(0), line);
        } else if (values.size() == 2) {
            start = Types.toLong(values.get(0), line);
            end = Types.toLong(values.get(1), line);
        } else if (values.size() == 3) {
            start = Types.toLong(values.get(0), line);
            end = Types.toLong(values.get(1), line);
            step = Types.toLong(values.get(2), line);
        } else {
            throw new DystosException("range attend 1, 2 ou 3 arguments", line);
        }

        if (step == 0) {
            throw new DystosException("range : le pas ne peut pas être zéro", line);
        }

        java.util.List<Object> items = new java.util.ArrayList<>();
        if (step > 0) {
            for (long i = start; i < end; i += step) {
                items.add(i);
            }
        } else {
            for (long i = start; i > end; i += step) {
                items.add(i);
            }
        }
        return items;
    }

    private static Object length(java.util.List<Object> values, int line) {
        Object value = argument(values, 0, "len", line);
        if (value instanceof String) {
            return (long) ((String) value).length();
        }
        if (value instanceof java.util.List) {
            return (long) ((java.util.List<?>) value).size();
        }
        throw new DystosException("len attend une chaîne ou une liste", line);
    }

    private static Object absolute(Object value, int line) {
        if (value instanceof Long) {
            return Math.abs((Long) value);
        }
        if (value instanceof Double) {
            return Math.abs((Double) value);
        }
        throw new DystosException("abs attend un nombre", line);
    }

    private static Object extremum(java.util.List<Object> values, String name, int line,
                                   boolean minimum) {
        if (values.isEmpty()) {
            throw new DystosException(name + " attend au moins un argument", line);
        }
        Object best = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            Object candidate = values.get(i);
            int comparison = Binary.compare(candidate, best, line);
            if (minimum ? comparison < 0 : comparison > 0) {
                best = candidate;
            }
        }
        return best;
    }

    private static Object round(java.util.List<Object> values, int line) {
        Object value = argument(values, 0, "round", line);
        if (value instanceof Long) {
            return value;
        }
        if (value instanceof Double) {
            double rounded = Math.rint((Double) value);
            return values.size() > 1
                    ? (Object) (long) rounded
                    : (Object) rounded;
        }
        throw new DystosException("round attend un nombre", line);
    }

    private static Object argument(java.util.List<Object> values, int index,
                                   String name, int line) {
        if (values.size() <= index) {
            throw new DystosException("'" + name + "' attend au moins "
                    + (index + 1) + " argument(s)", line);
        }
        return values.get(index);
    }

    private static Object toInt(Object value, int line) {
        if (value instanceof Long) {
            return value;
        }
        if (value instanceof Double) {
            return (long) (double) (Double) value;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? 1L : 0L;
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException e) {
                throw new DystosException("int : « " + value + " » n'est pas un entier", line);
            }
        }
        throw new DystosException("int attend un nombre", line);
    }

    private static Object toFloat(Object value, int line) {
        if (value instanceof Double) {
            return value;
        }
        if (value instanceof Long) {
            return ((Long) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException e) {
                throw new DystosException("float : « " + value + " » n'est pas un nombre", line);
            }
        }
        throw new DystosException("float attend un nombre", line);
    }

    /** Construit une déclaration synthétique pour une fonction intégrée. */
    private static FunctionDeclaration declarationFor(String name) {
        FunctionDeclaration declaration = new FunctionDeclaration(
                name, java.util.Arrays.asList(parametersFor(name)), null,
                java.util.Collections.emptyList(), 0);
        return declaration;
    }

    private static Parameter[] parametersFor(String name) {
        switch (name) {
            case "print":
            case "str":
            case "int":
            case "float":
            case "bool":
            case "abs":
                return new Parameter[] {new Parameter("valeur", null, null)};
            case "range":
                return new Parameter[] {
                        new Parameter("début", "int", new Literal(0L, 0)),
                        new Parameter("fin", "int", null),
                        new Parameter("pas", "int", new Literal(1L, 0)),
                };
            case "len":
                return new Parameter[] {new Parameter("valeur", null, null)};
            case "min":
            case "max":
                return new Parameter[] {new Parameter("valeurs", null, null)};
            case "round":
                return new Parameter[] {
                        new Parameter("valeur", "float", null),
                        new Parameter("chiffres", "int", new Literal(0L, 0)),
                };
            default:
                return new Parameter[0];
        }
    }
}