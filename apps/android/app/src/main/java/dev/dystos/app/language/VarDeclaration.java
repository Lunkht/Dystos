package dev.dystos.app.language;

/** Déclaration {@code val} ou {@code var}. */
public final class VarDeclaration implements Statement {

    private final String name;
    private final String declaredType;
    private final boolean mutable;
    private final Expression value;
    private final int line;

    public VarDeclaration(String name, String declaredType, boolean mutable,
                          Expression value, int line) {
        this.name = name;
        this.declaredType = declaredType;
        this.mutable = mutable;
        this.value = value;
        this.line = line;
    }

    public String name() {
        return name;
    }

    public String declaredType() {
        return declaredType;
    }

    public boolean isMutable() {
        return mutable;
    }

    public Expression value() {
        return value;
    }

    @Override
    public int line() {
        return line;
    }
}