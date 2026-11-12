package dev.dystos.app.language;

/** Instruction {@code break}. */
public final class BreakStatement implements Statement {

    private final int line;

    public BreakStatement(int line) {
        this.line = line;
    }

    @Override
    public int line() {
        return line;
    }
}