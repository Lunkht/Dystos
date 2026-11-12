package dev.dystos.app.language;

/** Valeur littérale : nombre, chaîne ou booléen. */
public final class Literal implements Expression {

    private final Object value;
    private final int line;

    public Literal(Object value, int line) {
        this.value = value;
        this.line = line;
    }

    public Object value() {
        return value;
    }

    @Override
    public int line() {
        return line;
    }
}