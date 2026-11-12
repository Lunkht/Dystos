package dev.dystos.app.language;

/** Référence à une variable ou à une fonction. */
public final class Variable implements Expression {

    private final String name;
    private final int line;

    public Variable(String name, int line) {
        this.name = name;
        this.line = line;
    }

    public String name() {
        return name;
    }

    @Override
    public int line() {
        return line;
    }
}