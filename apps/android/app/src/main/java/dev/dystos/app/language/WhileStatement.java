package dev.dystos.app.language;

import java.util.List;

/** Boucle {@code while condition:}. */
public final class WhileStatement implements Statement {

    private final Condition condition;
    private final List<Statement> body;
    private final int line;

    public WhileStatement(Condition condition, List<Statement> body, int line) {
        this.condition = condition;
        this.body = body;
        this.line = line;
    }

    public Condition condition() {
        return condition;
    }

    public List<Statement> body() {
        return body;
    }

    @Override
    public int line() {
        return line;
    }
}