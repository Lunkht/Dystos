package com.vulsoft.dystos.parser;

/** Erreur syntaxique avec position dans le source. */
public final class ParseException extends RuntimeException {

    private final int line;
    private final int column;

    public ParseException(String message, int line, int column) {
        super("ligne " + line + ", colonne " + column + " : " + message);
        this.line = line;
        this.column = column;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }
}
