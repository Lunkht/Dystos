package dev.dystos.app.language;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lexer Dystos.
 *
 * Dystos est indenté : l'indentation délimite les blocs, il n'y a ni accolade
 * ni mot-clé {@code end}. Le lexer produit donc des jetons {@code INDENT} et
 * {@code DEDENT} à partir des changements de colonnes de départ.
 *
 * Une tabulation vaut quatre espaces. Mélanger les deux sur une même ligne est
 * refusé, comme dans la plupart des langages.
 */
public final class Lexer {

    private static final Map<String, Boolean> KEYWORDS = new HashMap<>();

    static {
        for (String keyword : Arrays.asList(
                "val", "var", "def", "return", "if", "elif", "else", "for", "in",
                "while", "and", "or", "not", "true", "false", "break", "continue",
                "class", "interface", "enum", "abstract", "final", "import",
                "package", "match", "case", "pass", "try", "except", "finally",
                "raise", "assert", "with", "as", "is", "null")) {
            KEYWORDS.put(keyword, Boolean.TRUE);
        }
    }

    /** Les opérateurs sont testés du plus long au plus court. */
    private static final String[] OPERATORS = {
            "->", "==", "!=", "<=", ">=", "+=", "-=", "*=", "/=", "%=",
            "+", "-", "*", "/", "%", "=", "<", ">", "(", ")", "[", "]",
            "{", "}", ",", ":", ".", "!",
    };

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private final List<Integer> indentStack = new ArrayList<>();
    private int index;
    private int line = 1;
    private int column = 1;
    private int pendingIndent;

    private Lexer(String source) {
        this.source = source;
    }

    /** Analyse le source complet et renvoie la liste de jetons. */
    public static List<Token> tokenize(String source) {
        return new Lexer(source).run();
    }

    /**
     * Analyse jusqu'à la première erreur et renvoie ce qui a été lu.
     *
     * L'éditeur en a besoin : un programme en cours de saisie est forcément
     * incomplet, et colorer ce qui précède l'erreur vaut mieux qu'un écran
     * vide. L'analyse complète, elle, lève toujours l'exception.
     */
    static Lexed partialTokenize(String source) {
        Lexer lexer = new Lexer(source);
        try {
            List<Token> tokens = lexer.run();
            return new Lexed(tokens, null);
        } catch (DystosException e) {
            return new Lexed(lexer.tokens, e);
        }
    }

    /** Jetons lus jusqu'à l'erreur, et l'erreur elle-même si elle a eu lieu. */
    static final class Lexed {
        final List<Token> tokens;
        final DystosException error;

        Lexed(List<Token> tokens, DystosException error) {
            this.tokens = tokens;
            this.error = error;
        }
    }

    private List<Token> run() {
        indentStack.add(0);
        boolean atLineStart = true;

        while (index < source.length()) {
            char c = source.charAt(index);

            if (atLineStart) {
                if (c == '\n') {
                    // Ligne vide : elle n'a pas d'indentation et ne produit
                    // aucun jeton INDENT ou DEDENT.
                    newline();
                    continue;
                }
                if (c == ' ' || c == '\t') {
                    measureIndent(c);
                    continue;
                }
                if (c == '#') {
                    skipComment();
                    continue;
                }
                if (c == '\r') {
                    advance();
                    continue;
                }
                applyIndent();
                atLineStart = false;
            }

            if (c == '\n') {
                newline();
                atLineStart = true;
                continue;
            }

            if (c == ' ' || c == '\t') {
                advance();
                continue;
            }

            if (c == '#') {
                skipComment();
                continue;
            }

            if (c == '\\' && isLineContinuation()) {
                advance();
                continue;
            }

            if (c == 'f' || c == 'F') {
                Token token = tryFormattedString();
                if (token != null) {
                    tokens.add(token);
                    continue;
                }
            }

            if (c == '"' || c == '\'') {
                tokens.add(readString(c, false));
                continue;
            }

            if (Character.isDigit(c)) {
                tokens.add(readNumber());
                continue;
            }

            if (isIdentifierStart(c)) {
                tokens.add(readIdentifier());
                continue;
            }

            if (c == '\r') {
                advance();
                continue;
            }

            Token operator = readOperator();
            if (operator == null) {
                throw new DystosException("caractère inattendu : '" + c + "'", line);
            }
            tokens.add(operator);
        }

        endLine();
        while (indentStack.size() > 1) {
            indentStack.remove(indentStack.size() - 1);
            tokens.add(new Token(TokenKind.DEDENT, "", line, column));
        }
        tokens.add(new Token(TokenKind.EOF, "", line, column));
        return tokens;
    }

    /**
     * Mesure l'indentation d'une ligne sans encore produire de jeton.
     *
     * L'indentation n'est appliquée que lorsque la ligne contient réellement du
     * code : une ligne vide au milieu d'un bloc ne le ferme pas.
     */
    private void measureIndent(char c) {
        if (c == '\t') {
            if (pendingIndent % 4 != 0) {
                throw new DystosException(
                        "tabulation inattendue dans l'indentation", line);
            }
            pendingIndent += 4;
        } else {
            pendingIndent++;
        }
        advance();
    }

    /** Émet INDENT ou DEDENT selon l'indentation mesurée. */
    private void applyIndent() {
        int current = indentStack.get(indentStack.size() - 1);
        if (pendingIndent > current) {
            indentStack.add(pendingIndent);
            tokens.add(new Token(TokenKind.INDENT, "", line, column));
        } else if (pendingIndent < current) {
            while (indentStack.size() > 1
                    && pendingIndent < indentStack.get(indentStack.size() - 1)) {
                indentStack.remove(indentStack.size() - 1);
                tokens.add(new Token(TokenKind.DEDENT, "", line, column));
            }
            if (pendingIndent != indentStack.get(indentStack.size() - 1)) {
                throw new DystosException(
                        "indentation incohérente avec le bloc précédent", line);
            }
        }
    }

    private void newline() {
        endLine();
        advance();
        line++;
        column = 1;
        pendingIndent = 0;
    }

    private void endLine() {
        Token last = tokens.isEmpty() ? null : tokens.get(tokens.size() - 1);
        if (last == null) {
            return;
        }
        if (last.kind() != TokenKind.NEWLINE
                && last.kind() != TokenKind.INDENT
                && last.kind() != TokenKind.DEDENT) {
            tokens.add(new Token(TokenKind.NEWLINE, "\n", line, column));
        }
    }

    private void skipComment() {
        while (index < source.length() && source.charAt(index) != '\n') {
            advance();
        }
    }

    /** Un antislash en fin de ligne poursuit la ligne logique. */
    private boolean isLineContinuation() {
        int next = index + 1;
        while (next < source.length()
                && (source.charAt(next) == ' ' || source.charAt(next) == '\t')) {
            next++;
        }
        return next < source.length() && source.charAt(next) == '\n';
    }

    /** Reconnaît une chaîne formatée {@code f"..."}, sinon renvoie null. */
    private Token tryFormattedString() {
        int probe = index + 1;
        if (probe < source.length() && source.charAt(probe) == '"') {
            return readString('"', true);
        }
        return null;
    }

    private Token readString(char quote, boolean formatted) {
        int startLine = line;
        int startColumn = column;
        if (formatted) {
            advance();
        }
        advance();

        StringBuilder text = new StringBuilder();
        while (index < source.length() && source.charAt(index) != quote) {
            char c = source.charAt(index);
            if (c == '\n') {
                throw new DystosException("chaîne non terminée", startLine);
            }
            if (c == '\\') {
                advance();
                text.append(readEscape(startLine));
                continue;
            }
            text.append(c);
            advance();
        }

        if (index >= source.length()) {
            throw new DystosException("chaîne non terminée", startLine);
        }
        advance();

        TokenKind kind = formatted ? TokenKind.FORMATTED_STRING : TokenKind.STRING;
        return new Token(kind, text.toString(), startLine, startColumn);
    }

    private char readEscape(int startLine) {
        if (index >= source.length()) {
            throw new DystosException("séquence d'échappement incomplète", startLine);
        }
        char c = source.charAt(index);
        advance();
        switch (c) {
            case 'n':
                return '\n';
            case 't':
                return '\t';
            case 'r':
                return '\r';
            case '\\':
                return '\\';
            case '"':
                return '"';
            case '\'':
                return '\'';
            default:
                throw new DystosException(
                        "séquence d'échappement inconnue : '\\" + c + "'", startLine);
        }
    }

    private Token readNumber() {
        int startLine = line;
        int startColumn = column;
        StringBuilder text = new StringBuilder();
        boolean isFloat = false;

        while (index < source.length()) {
            char c = source.charAt(index);
            if (Character.isDigit(c)) {
                text.append(c);
                advance();
            } else if (c == '_') {
                advance();
            } else if (c == '.' && index + 1 < source.length()
                    && Character.isDigit(source.charAt(index + 1))) {
                isFloat = true;
                text.append(c);
                advance();
            } else {
                break;
            }
        }

        if (startsExponent()) {
            isFloat = true;
            text.append(source.charAt(index));
            advance();
            if (index < source.length()
                    && (source.charAt(index) == '+' || source.charAt(index) == '-')) {
                text.append(source.charAt(index));
                advance();
            }
            int digits = index;
            while (index < source.length() && Character.isDigit(source.charAt(index))) {
                text.append(source.charAt(index));
                advance();
            }
            if (index == digits) {
                throw new DystosException(
                        "notation scientifique sans chiffre après 'e'", startLine);
            }
        }

        return new Token(isFloat ? TokenKind.FLOAT : TokenKind.INT,
                text.toString(), startLine, startColumn);
    }

    /**
     * Un exposant n'est reconnu que s'il est suivi de chiffres, sinon
     * l'identifiant qui commence par 'e' serait coupé en deux.
     */
    private boolean startsExponent() {
        if (index >= source.length()) {
            return false;
        }
        char c = source.charAt(index);
        if (c != 'e' && c != 'E') {
            return false;
        }
        int next = index + 1;
        if (next < source.length()
                && (source.charAt(next) == '+' || source.charAt(next) == '-')) {
            next++;
        }
        return next < source.length() && Character.isDigit(source.charAt(next));
    }

    private Token readIdentifier() {
        int startLine = line;
        int startColumn = column;
        StringBuilder text = new StringBuilder();
        while (index < source.length() && isIdentifierPart(source.charAt(index))) {
            text.append(source.charAt(index));
            advance();
        }
        String value = text.toString();
        TokenKind kind = KEYWORDS.containsKey(value) ? TokenKind.KEYWORD : TokenKind.IDENT;
        return new Token(kind, value, startLine, startColumn);
    }

    private Token readOperator() {
        int startLine = line;
        int startColumn = column;
        for (String operator : OPERATORS) {
            if (source.startsWith(operator, index)) {
                for (int i = 0; i < operator.length(); i++) {
                    advance();
                }
                return new Token(TokenKind.OPERATOR, operator, startLine, startColumn);
            }
        }
        return null;
    }

    private boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private void advance() {
        index++;
        column++;
    }
}