package dev.dystos.app.language;

import java.util.List;

/** Valeur de fonction : une déclaration {@code def} prête à être appelée. */
public final class FunctionValue {

    private final FunctionDeclaration declaration;

    FunctionValue(FunctionDeclaration declaration) {
        this.declaration = declaration;
    }

    public String name() {
        return declaration.name();
    }

    public String returnType() {
        return declaration.returnType();
    }

    public List<Parameter> parameters() {
        return declaration.parameters();
    }

    public List<Statement> body() {
        return declaration.body();
    }

    public int line() {
        return declaration.line();
    }

    @Override
    public String toString() {
        return "<fonction " + name() + ">";
    }
}