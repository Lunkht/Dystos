package com.vulsoft.dystos.lexer;

/** Erreur lexicale avec position dans le source. */
public final class LexerException extends RuntimeException {

    private final int line;
    private final int column;

    public LexerException(String message, int line, int column) {
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
