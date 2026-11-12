package dev.dystos.app.language;

/** Expression utilisée comme instruction. */
public final class ExpressionStatement implements Statement {

    private final Expression expression;
    private final int line;

    public ExpressionStatement(Expression expression) {
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