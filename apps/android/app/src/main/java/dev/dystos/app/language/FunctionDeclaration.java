package dev.dystos.app.language;

import java.util.List;

/** Déclaration {@code def}. */
public final class FunctionDeclaration implements Statement {

    private final String name;
    private final List<Parameter> parameters;
    private final String returnType;
    private final List<Statement> body;
    private final int line;

    public FunctionDeclaration(String name, List<Parameter> parameters, String returnType,
                               List<Statement> body, int line) {
        this.name = name;
        this.parameters = parameters;
        this.returnType = returnType;
        this.body = body;
        this.line = line;
    }

    public String name() {
        return name;
    }

    public List<Parameter> parameters() {
        return parameters;
    }

    /** Type de retour déclaré, ou null si la fonction renvoie {@code void}. */
    public String returnType() {
        return returnType;
    }

    public List<Statement> body() {
        return body;
    }

    @Override
    public int line() {
        return line;
    }
}