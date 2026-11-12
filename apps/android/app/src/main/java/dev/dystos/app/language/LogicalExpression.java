package dev.dystos.app.language;

/** Opération logique {@code and} / {@code or}, à court-circuit. */
public final class LogicalExpression implements Expression {

    private final Expression left;
    private final String operator;
    private final Expression right;
    private final int line;

    public LogicalExpression(Expression left, String operator, Expression right, int line) {
        this.left = left;
        this.operator = operator;
        this.right = right;
        this.line = line;
    }

    public Expression left() {
        return left;
    }

    public String operator() {
        return operator;
    }

    public Expression right() {
        return right;
    }

    @Override
    public int line() {
        return line;
    }
}