package dev.dystos.app.language;

/**
 * Paramètre de fonction.
 *
 * Le type et la valeur par défaut sont optionnels : Dystos autorise
 * {@code def f(x)} comme {@code def f(x: int = 0)}.
 */
public final class Parameter {

    private final String name;
    private final String declaredType;
    private final Expression defaultValue;

    public Parameter(String name, String declaredType, Expression defaultValue) {
        this.name = name;
        this.declaredType = declaredType;
        this.defaultValue = defaultValue;
    }

    public String name() {
        return name;
    }

    public String declaredType() {
        return declaredType;
    }

    public Expression defaultValue() {
        return defaultValue;
    }
}