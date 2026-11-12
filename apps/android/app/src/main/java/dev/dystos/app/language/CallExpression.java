package dev.dystos.app.language;

import java.util.List;

/** Appel d'une valeur de type fonction. */
public final class CallExpression implements Expression {

    private final Expression callee;
    private final List<Expression> arguments;
    private final int line;

    public CallExpression(Expression callee, List<Expression> arguments, int line) {
        this.callee = callee;
        this.arguments = arguments;
        this.line = line;
    }

    public Expression callee() {
        return callee;
    }

    public List<Expression> arguments() {
        return arguments;
    }

    @Override
    public int line() {
        return line;
    }
}