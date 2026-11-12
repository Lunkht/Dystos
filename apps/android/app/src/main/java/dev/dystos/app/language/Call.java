package dev.dystos.app.language;

import java.util.List;

/**
 * Appel de fonction nommée.
 *
 * Un argument nommé peut être fourni ; {@code argumentNames} contient le nom
 * à la même position que l'argument, ou {@code null} si l'argument est
 * positionnel.
 */
public final class Call implements Expression {

    private final String name;
    private final List<Expression> arguments;
    private final List<String> argumentNames;
    private final int line;

    public Call(String name, List<Expression> arguments, List<String> argumentNames, int line) {
        this.name = name;
        this.arguments = arguments;
        this.argumentNames = argumentNames;
        this.line = line;
    }

    public String name() {
        return name;
    }

    public List<Expression> arguments() {
        return arguments;
    }

    public List<String> argumentNames() {
        return argumentNames;
    }

    public int size() {
        return arguments.size();
    }

    public Expression argument(int position) {
        return arguments.get(position);
    }

    public String argumentName(int position) {
        return argumentNames == null ? null : argumentNames.get(position);
    }

    @Override
    public int line() {
        return line;
    }
}