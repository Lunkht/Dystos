package dev.dystos.app.language;

/** Catégories de jetons produites par le lexer. */
enum TokenKind {
    IDENT,
    INT,
    FLOAT,
    STRING,
    FORMATTED_STRING,
    KEYWORD,
    OPERATOR,
    NEWLINE,
    INDENT,
    DEDENT,
    EOF
}