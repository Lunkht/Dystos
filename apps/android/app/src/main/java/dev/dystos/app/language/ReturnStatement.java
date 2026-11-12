package dev.dystos.app.language;

/** Instruction {@code return}, avec ou sans valeur. */
public final class ReturnStatement implements Statement {

    private final Expression value;
    private final int line;

    public ReturnStatement(Expression value, int line) {
        this.value = value;
        this.line = line;
    }

    public Expression value() {
        return value;
    }

    @Override
    public int line() {
        return line;
    }
}