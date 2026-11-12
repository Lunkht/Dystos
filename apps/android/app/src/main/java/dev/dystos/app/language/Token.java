package dev.dystos.app.language;

/** Un jeton du langage Dystos, avec sa position dans le source. */
final class Token {

    private final TokenKind kind;
    private final String text;
    private final int line;
    private final int column;

    Token(TokenKind kind, String text, int line, int column) {
        this.kind = kind;
        this.text = text;
        this.line = line;
        this.column = column;
    }

    TokenKind kind() {
        return kind;
    }

    String text() {
        return text;
    }

    int line() {
        return line;
    }

    int column() {
        return column;
    }

    @Override
    public String toString() {
        return kind + "('" + text + "') " + line + ":" + column;
    }
}