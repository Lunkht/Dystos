package dev.dystos.app.language;

/** Instruction {@code continue}. */
public final class ContinueStatement implements Statement {

    private final int line;

    public ContinueStatement(int line) {
        this.line = line;
    }

    @Override
    public int line() {
        return line;
    }
}