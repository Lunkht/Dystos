package com.vulsoft.dystos.lexer;

/**
 * Un lexème.
 *
 * @param type   catégorie du jeton
 * @param text   texte du jeton (voir {@link TokenType#STRING} et {@link TokenType#FSTRING} ;
 *               pour INT et FLOAT, le texte source sans les soulignés « _ »)
 * @param line   ligne de début, à partir de 1
 * @param column colonne de début, à partir de 1
 */
public record Token(TokenType type, String text, int line, int column) {

    @Override
    public String toString() {
        return switch (type) {
            case NAME, INT, FLOAT, STRING, FSTRING -> type + "(" + text + ")@" + line + ":" + column;
            default -> type + "@" + line + ":" + column;
        };
    }
}
