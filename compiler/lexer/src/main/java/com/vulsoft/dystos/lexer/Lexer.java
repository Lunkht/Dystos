package com.vulsoft.dystos.lexer;

import static com.vulsoft.dystos.lexer.TokenType.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Analyseur lexical de Dystos.
 *
 * <p>Particularités :
 * <ul>
 *   <li>Blocs par indentation : émet INDENT / DEDENT (espaces uniquement, tabulations refusées
 *       dans l'indentation). Tout nombre d'espaces est accepté s'il est cohérent.</li>
 *   <li>À l'intérieur de ( ), [ ] et { }, les retours à la ligne et l'indentation sont ignorés.</li>
 *   <li>Les lignes vides et les lignes de commentaire (#) ne produisent aucun jeton.</li>
 *   <li>Ambiguïté {@code ?:} : si le « : » termine la ligne (ex. {@code -> Produit?:}),
 *       on émet QUESTION puis COLON ; sinon on émet l'opérateur ELVIS ({@code a ?: b}).</li>
 *   <li>Les f-strings ne sont pas découpées ici : le jeton FSTRING contient le texte brut.</li>
 * </ul>
 */
public final class Lexer {

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("def", DEF), Map.entry("class", CLASS), Map.entry("interface", INTERFACE),
            Map.entry("enum", ENUM), Map.entry("abstract", ABSTRACT), Map.entry("final", FINAL),
            Map.entry("import", IMPORT), Map.entry("package", PACKAGE),
            Map.entry("if", IF), Map.entry("elif", ELIF), Map.entry("else", ELSE),
            Map.entry("for", FOR), Map.entry("while", WHILE), Map.entry("in", IN),
            Map.entry("return", RETURN), Map.entry("break", BREAK),
            Map.entry("continue", CONTINUE), Map.entry("pass", PASS),
            Map.entry("try", TRY), Map.entry("except", EXCEPT), Map.entry("finally", FINALLY),
            Map.entry("raise", RAISE), Map.entry("assert", ASSERT),
            Map.entry("match", MATCH), Map.entry("case", CASE),
            Map.entry("with", WITH), Map.entry("as", AS),
            Map.entry("val", VAL), Map.entry("var", VAR),
            Map.entry("true", TRUE), Map.entry("false", FALSE), Map.entry("null", NULL),
            Map.entry("and", AND), Map.entry("or", OR), Map.entry("not", NOT), Map.entry("is", IS));

    private final String src;
    private final int length;
    private int pos = 0;
    private int line = 1;
    private int column = 1;
    private boolean atLineStart = true;

    private final Deque<Integer> indents = new ArrayDeque<>();
    /** Délimiteurs ouverts : {caractère, ligne, colonne}. */
    private final Deque<int[]> openers = new ArrayDeque<>();
    private final List<Token> tokens = new ArrayList<>();

    private Lexer(String source) {
        String s = source.startsWith("\uFEFF") ? source.substring(1) : source;
        this.src = s.replace("\r\n", "\n").replace('\r', '\n');
        this.length = src.length();
        indents.push(0);
    }

    /** Découpe un source Dystos en jetons. La liste se termine toujours par EOF. */
    public static List<Token> tokenize(String source) {
        return new Lexer(source).run();
    }

    // ------------------------------------------------------------------
    // Boucle principale
    // ------------------------------------------------------------------

    private List<Token> run() {
        while (pos < length) {
            if (atLineStart && openers.isEmpty()) {
                if (!lexIndentation()) {
                    continue;
                }
            }
            scanToken();
        }
        finish();
        return tokens;
    }

    private void finish() {
        if (!openers.isEmpty()) {
            int[] o = openers.peek();
            throw error(o[1], o[2], "'" + (char) o[0] + "' jamais fermé");
        }
        if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).type() != NEWLINE) {
            tokens.add(new Token(NEWLINE, "\n", line, column));
        }
        while (indents.size() > 1) {
            indents.pop();
            tokens.add(new Token(DEDENT, "", line, column));
        }
        tokens.add(new Token(EOF, "", line, column));
    }

    // ------------------------------------------------------------------
    // Indentation
    // ------------------------------------------------------------------

    /** Traite le début de ligne. Retourne false si la ligne est vide ou ne contient qu'un commentaire. */
    private boolean lexIndentation() {
        int tl = line;
        int tc = column;
        int spaces = 0;
        while (peek() == ' ') {
            advance();
            spaces++;
        }
        if (pos >= length) {
            return false;
        }
        char c = peek();
        if (c == '\t') {
            throw error(line, column, "tabulation interdite dans l'indentation (utilisez des espaces)");
        }
        if (c == '\n') {
            advance();
            return false;
        }
        if (c == '#') {
            skipComment();
            return false;
        }

        int top = indents.peek();
        if (spaces > top) {
            indents.push(spaces);
            tokens.add(new Token(INDENT, "", tl, tc));
        } else if (spaces < top) {
            while (spaces < indents.peek()) {
                indents.pop();
                tokens.add(new Token(DEDENT, "", tl, tc));
            }
            if (spaces != indents.peek()) {
                throw error(tl, tc, "indentation incohérente (aucun bloc ouvert à ce niveau)");
            }
        }
        atLineStart = false;
        return true;
    }

    private void skipComment() {
        while (pos < length && peek() != '\n') {
            advance();
        }
    }

    // ------------------------------------------------------------------
    // Jetons
    // ------------------------------------------------------------------

    private void scanToken() {
        char c = peek();
        int tl = line;
        int tc = column;

        if (c == ' ' || c == '\t') {
            advance();
        } else if (c == '\n') {
            advance();
            if (openers.isEmpty()) {
                tokens.add(new Token(NEWLINE, "\n", tl, tc));
                atLineStart = true;
            }
        } else if (c == '#') {
            skipComment();
        } else if (c == '\\') {
            if (peek(1) != '\n') {
                throw error(tl, tc, "caractère '\\' inattendu (continuation de ligne : '\\' en fin de ligne)");
            }
            advance();
            advance();
        } else if (isDigit(c)) {
            number(tl, tc);
        } else if (Character.isLetter(c) || c == '_') {
            identifier(tl, tc);
        } else if (c == '"' || c == '\'') {
            string(false, tl, tc);
        } else {
            operator(tl, tc);
        }
    }

    private void identifier(int tl, int tc) {
        int start = pos;
        while (Character.isLetterOrDigit(peek()) || peek() == '_') {
            advance();
        }
        String text = src.substring(start, pos);
        if ((text.equals("f") || text.equals("F")) && (peek() == '"' || peek() == '\'')) {
            string(true, tl, tc);
            return;
        }
        tokens.add(new Token(KEYWORDS.getOrDefault(text, NAME), text, tl, tc));
    }

    // ------------------------------------------------------------------
    // Nombres
    // ------------------------------------------------------------------

    private void number(int tl, int tc) {
        int start = pos;
        boolean isFloat = false;

        if (peek() == '0' && (peek(1) == 'x' || peek(1) == 'X')) {
            advance();
            advance();
            if (!isHex(peek())) {
                throw error(tl, tc, "littéral hexadécimal incomplet");
            }
            while (isHex(peek()) || peek() == '_') {
                advance();
            }
        } else if (peek() == '0' && (peek(1) == 'b' || peek(1) == 'B')) {
            advance();
            advance();
            if (peek() != '0' && peek() != '1') {
                throw error(tl, tc, "littéral binaire incomplet");
            }
            while (peek() == '0' || peek() == '1' || peek() == '_') {
                advance();
            }
        } else {
            digits();
            if (peek() == '.' && isDigit(peek(1))) {
                isFloat = true;
                advance();
                digits();
            }
            boolean signedExp = (peek(1) == '+' || peek(1) == '-') && isDigit(peek(2));
            if ((peek() == 'e' || peek() == 'E') && (isDigit(peek(1)) || signedExp)) {
                isFloat = true;
                advance();
                if (peek() == '+' || peek() == '-') {
                    advance();
                }
                digits();
            }
        }
        if (Character.isLetter(peek()) || peek() == '_') {
            throw error(tl, tc, "littéral numérique invalide");
        }
        String text = src.substring(start, pos).replace("_", "");
        tokens.add(new Token(isFloat ? FLOAT : INT, text, tl, tc));
    }

    private void digits() {
        while (isDigit(peek()) || peek() == '_') {
            advance();
        }
    }

    // ------------------------------------------------------------------
    // Chaînes
    // ------------------------------------------------------------------

    private void string(boolean formatted, int tl, int tc) {
        char quote = advance();
        boolean triple = false;
        if (peek() == quote && peek(1) == quote) {
            advance();
            advance();
            triple = true;
        } else if (peek() == quote) {
            advance();
            tokens.add(new Token(formatted ? FSTRING : STRING, "", tl, tc));
            return;
        }

        StringBuilder sb = new StringBuilder();
        int depth = 0;
        while (true) {
            if (pos >= length) {
                throw error(tl, tc, "chaîne non terminée");
            }
            char c = peek();
            if (c == '\n' && !triple) {
                throw error(tl, tc, "chaîne non terminée (retour à la ligne dans une chaîne simple)");
            }
            if (atClosing(quote, triple)) {
                advance();
                if (triple) {
                    advance();
                    advance();
                }
                break;
            }
            if (!formatted) {
                if (c == '\\') {
                    sb.append(escape());
                } else {
                    sb.append(advance());
                }
                continue;
            }

            // f-string : texte brut, avec suivi de la profondeur des accolades
            if (c == '\\') {
                sb.append(advance());
                if (pos < length) {
                    sb.append(advance());
                }
            } else if (depth == 0 && c == '{' && peek(1) == '{') {
                sb.append(advance()).append(advance());
            } else if (depth == 0 && c == '}' && peek(1) == '}') {
                sb.append(advance()).append(advance());
            } else if (depth == 0 && c == '}') {
                throw error(line, column, "'}' isolée dans une f-string (écrire '}}')");
            } else if (c == '{') {
                depth++;
                sb.append(advance());
            } else if (c == '}') {
                depth--;
                sb.append(advance());
            } else if (depth > 0 && (c == '"' || c == '\'') && c != quote) {
                sb.append(advance());
                while (pos < length && peek() != c && peek() != '\n') {
                    sb.append(advance());
                }
                if (peek() != c) {
                    throw error(tl, tc, "chaîne non terminée dans une expression de f-string");
                }
                sb.append(advance());
            } else {
                sb.append(advance());
            }
        }
        if (formatted && depth != 0) {
            throw error(tl, tc, "'{' non fermée dans la f-string");
        }
        tokens.add(new Token(formatted ? FSTRING : STRING, sb.toString(), tl, tc));
    }

    private boolean atClosing(char quote, boolean triple) {
        if (peek() != quote) {
            return false;
        }
        return !triple || (peek(1) == quote && peek(2) == quote);
    }

    private String escape() {
        int el = line;
        int ec = column;
        advance(); // '\'
        if (pos >= length) {
            throw error(el, ec, "séquence d'échappement incomplète");
        }
        char e = advance();
        switch (e) {
            case 'n': return "\n";
            case 't': return "\t";
            case 'r': return "\r";
            case '0': return "\0";
            case '\\': return "\\";
            case '\'': return "'";
            case '"': return "\"";
            case '\n': return "";
            case 'u': {
                if (pos + 4 > length) {
                    throw error(el, ec, "séquence \\u incomplète");
                }
                String hex = src.substring(pos, pos + 4);
                int cp;
                try {
                    cp = Integer.parseInt(hex, 16);
                } catch (NumberFormatException ex) {
                    throw error(el, ec, "séquence \\u invalide : \\u" + hex);
                }
                for (int i = 0; i < 4; i++) {
                    advance();
                }
                return String.valueOf((char) cp);
            }
            default:
                throw error(el, ec, "séquence d'échappement inconnue : \\" + e);
        }
    }

    // ------------------------------------------------------------------
    // Opérateurs et ponctuation
    // ------------------------------------------------------------------

    private void operator(int tl, int tc) {
        int start = pos;
        char c = advance();
        TokenType t;
        switch (c) {
            case '+': t = match('=') ? PLUS_ASSIGN : PLUS; break;
            case '-': t = match('>') ? ARROW : match('=') ? MINUS_ASSIGN : MINUS; break;
            case '*': t = match('*') ? STARSTAR : match('=') ? STAR_ASSIGN : STAR; break;
            case '/': t = match('=') ? SLASH_ASSIGN : SLASH; break;
            case '%': t = match('=') ? PERCENT_ASSIGN : PERCENT; break;
            case '=': t = match('=') ? EQ : ASSIGN; break;
            case '<': t = match('=') ? LE : LT; break;
            case '>': t = match('=') ? GE : GT; break;
            case '!':
                if (!match('=')) {
                    throw error(tl, tc, "caractère '!' inattendu (utilisez 'not' ou '!=')");
                }
                t = NE;
                break;
            case '?':
                if (match('.')) {
                    t = QDOT;
                } else if (peek() == ':' && !colonEndsLine(pos + 1)) {
                    advance();
                    t = ELVIS;
                } else {
                    t = QUESTION;
                }
                break;
            case '|': t = PIPE; break;
            case '.': t = DOT; break;
            case ',': t = COMMA; break;
            case ':': t = COLON; break;
            case '@': t = AT; break;
            case '(': open(c, tl, tc); t = LPAREN; break;
            case '[': open(c, tl, tc); t = LBRACKET; break;
            case '{': open(c, tl, tc); t = LBRACE; break;
            case ')': close(c, '(', tl, tc); t = RPAREN; break;
            case ']': close(c, '[', tl, tc); t = RBRACKET; break;
            case '}': close(c, '{', tl, tc); t = RBRACE; break;
            default:
                throw error(tl, tc, "caractère inattendu : '" + c + "'");
        }
        tokens.add(new Token(t, src.substring(start, pos), tl, tc));
    }

    /** Vrai si, à partir de l'index i, il ne reste que des blancs puis une fin de ligne ou un commentaire. */
    private boolean colonEndsLine(int i) {
        while (i < length && (src.charAt(i) == ' ' || src.charAt(i) == '\t')) {
            i++;
        }
        return i >= length || src.charAt(i) == '\n' || src.charAt(i) == '#';
    }

    private void open(char c, int tl, int tc) {
        openers.push(new int[] {c, tl, tc});
    }

    private void close(char c, char expectedOpener, int tl, int tc) {
        if (openers.isEmpty()) {
            throw error(tl, tc, "'" + c + "' sans délimiteur ouvrant");
        }
        int[] o = openers.pop();
        if (o[0] != expectedOpener) {
            throw error(tl, tc, "'" + c + "' ne correspond pas à '" + (char) o[0]
                    + "' ouvert ligne " + o[1] + ", colonne " + o[2]);
        }
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private char peek() {
        return pos < length ? src.charAt(pos) : '\0';
    }

    private char peek(int offset) {
        int i = pos + offset;
        return i < length ? src.charAt(i) : '\0';
    }

    private char advance() {
        char c = src.charAt(pos++);
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return c;
    }

    private boolean match(char expected) {
        if (peek() == expected && pos < length) {
            advance();
            return true;
        }
        return false;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isHex(char c) {
        return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private LexerException error(int l, int c, String message) {
        return new LexerException(message, l, c);
    }
}
