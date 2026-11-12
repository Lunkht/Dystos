package dev.dystos.app.language;

/** Condition de contrôle : une expression évaluée comme un booléen. */
public final class Condition implements Expression {

    private final Expression expression;
    private final int line;

    public Condition(Expression expression) {
        this.expression = expression;
        this.line = expression.line();
    }

    public Expression expression() {
        return expression;
    }

    @Override
    public int line() {
        return line;
    }
}