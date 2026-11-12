package dev.dystos.app.language;

import java.util.List;

/** Boucle {@code for nom in iterable:}. */
public final class ForStatement implements Statement {

    private final String variableName;
    private final Expression iterable;
    private final List<Statement> body;
    private final int line;

    public ForStatement(String variableName, Expression iterable, List<Statement> body, int line) {
        this.variableName = variableName;
        this.iterable = iterable;
        this.body = body;
        this.line = line;
    }

    public String variableName() {
        return variableName;
    }

    public Expression iterable() {
        return iterable;
    }

    public List<Statement> body() {
        return body;
    }

    @Override
    public int line() {
        return line;
    }
}