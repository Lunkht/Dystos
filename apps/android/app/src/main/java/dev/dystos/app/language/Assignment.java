package dev.dystos.app.language;

/** Affectation, éventuellement composée {@code +=} et assimilés. */
public final class Assignment implements Statement {

    private final String name;
    private final String compoundOperator;
    private final Expression value;
    private final int line;

    public Assignment(String name, String compoundOperator, Expression value, int line) {
        this.name = name;
        this.compoundOperator = compoundOperator;
        this.value = value;
        this.line = line;
    }

    public String name() {
        return name;
    }

    /** Opérateur composé sans le {@code =}, ou null pour une affectation simple. */
    public String compoundOperator() {
        return compoundOperator;
    }

    public Expression value() {
        return value;
    }

    @Override
    public int line() {
        return line;
    }
}