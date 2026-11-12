package dev.dystos.app.language;

/** Opération unaire : négation ou {@code not}. */
public final class UnaryExpression implements Expression {

    private final String operator;
    private final Expression operand;
    private final int line;

    public UnaryExpression(String operator, Expression operand, int line) {
        this.operator = operator;
        this.operand = operand;
        this.line = line;
    }

    public String operator() {
        return operator;
    }

    public Expression operand() {
        return operand;
    }

    @Override
    public int line() {
        return line;
    }
}