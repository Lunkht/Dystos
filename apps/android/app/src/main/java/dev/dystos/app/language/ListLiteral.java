package dev.dystos.app.language;

import java.util.List;

/** Liste littérale {@code [a, b, c]}. */
public final class ListLiteral implements Expression {

    private final List<Expression> items;
    private final int line;

    public ListLiteral(List<Expression> items, int line) {
        this.items = items;
        this.line = line;
    }

    public List<Expression> items() {
        return items;
    }

    @Override
    public int line() {
        return line;
    }
}