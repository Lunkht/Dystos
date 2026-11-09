package com.vulsoft.dystos.lexer;

/** Catégories de lexèmes du langage Dystos. */
public enum TokenType {

    // ---- Littéraux et identifiants ----
    NAME,
    INT,
    FLOAT,
    /** Chaîne simple : le texte du jeton est la valeur décodée (échappements résolus). */
    STRING,
    /** Chaîne formatée f"..." : le texte du jeton est le contenu brut, à découper par le parser. */
    FSTRING,

    // ---- Mots-clés ----
    DEF, CLASS, INTERFACE, ENUM, ABSTRACT, FINAL,
    IMPORT, PACKAGE,
    IF, ELIF, ELSE, FOR, WHILE, IN,
    RETURN, BREAK, CONTINUE, PASS,
    TRY, EXCEPT, FINALLY, RAISE, ASSERT,
    MATCH, CASE, WITH, AS,
    VAL, VAR,
    TRUE, FALSE, NULL,
    AND, OR, NOT, IS,

    // ---- Opérateurs arithmétiques ----
    PLUS, MINUS, STAR, SLASH, PERCENT, STARSTAR,

    // ---- Comparaisons ----
    EQ, NE, LT, GT, LE, GE,

    // ---- Affectations ----
    ASSIGN, PLUS_ASSIGN, MINUS_ASSIGN, STAR_ASSIGN, SLASH_ASSIGN, PERCENT_ASSIGN,

    // ---- Opérateurs spéciaux ----
    ARROW,      // ->
    QUESTION,   // ?   (type nullable : str?)
    QDOT,       // ?.  (accès sûr)
    ELVIS,      // ?:  (valeur par défaut)
    PIPE,       // |

    // ---- Ponctuation ----
    DOT, COMMA, COLON, AT,
    LPAREN, RPAREN, LBRACKET, RBRACKET, LBRACE, RBRACE,

    // ---- Structure ----
    NEWLINE, INDENT, DEDENT, EOF
}
