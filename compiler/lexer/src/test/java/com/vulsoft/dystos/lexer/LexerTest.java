package com.vulsoft.dystos.lexer;

import static com.vulsoft.dystos.lexer.TokenType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LexerTest {

    private static List<TokenType> types(String src) {
        return Lexer.tokenize(src).stream().map(Token::type).collect(Collectors.toList());
    }

    private static List<TokenType> list(TokenType... t) {
        return List.of(t);
    }

    // ---------------- Structure et indentation ----------------

    @Test
    void helloWorld() {
        assertEquals(
                list(DEF, NAME, LPAREN, RPAREN, COLON, NEWLINE,
                        INDENT, NAME, LPAREN, STRING, RPAREN, NEWLINE, DEDENT, EOF),
                types("def main():\n    print(\"Bonjour\")\n"));
    }

    @Test
    void emptySourceGivesOnlyEof() {
        assertEquals(list(EOF), types(""));
        assertEquals(list(EOF), types("\n\n   \n# juste un commentaire\n"));
    }

    @Test
    void multipleDedentsAtOnce() {
        String src = "if a:\n    if b:\n        x = 1\ny = 2\n";
        assertEquals(
                list(IF, NAME, COLON, NEWLINE,
                        INDENT, IF, NAME, COLON, NEWLINE,
                        INDENT, NAME, ASSIGN, INT, NEWLINE,
                        DEDENT, DEDENT, NAME, ASSIGN, INT, NEWLINE, EOF),
                types(src));
    }

    @Test
    void blankLinesAndCommentsProduceNoTokens() {
        String src = "x = 1  # fin de ligne\n\n    # commentaire indenté\n# autre\ny = 2\n";
        assertEquals(
                list(NAME, ASSIGN, INT, NEWLINE, NAME, ASSIGN, INT, NEWLINE, EOF),
                types(src));
    }

    @Test
    void sourceWithoutTrailingNewlineStillClosesBlocks() {
        assertEquals(
                list(IF, NAME, COLON, NEWLINE, INDENT, PASS, NEWLINE, DEDENT, EOF),
                types("if a:\n    pass"));
    }

    @Test
    void newlinesAreIgnoredInsideBrackets() {
        String src = "x = [1,\n  2,\n        3]\n";
        assertEquals(
                list(NAME, ASSIGN, LBRACKET, INT, COMMA, INT, COMMA, INT, RBRACKET, NEWLINE, EOF),
                types(src));
    }

    @Test
    void backslashContinuesALine() {
        assertEquals(list(NAME, ASSIGN, INT, PLUS, INT, NEWLINE, EOF), types("x = 1 + \\\n    2\n"));
    }

    @Test
    void crlfIsNormalized() {
        assertEquals(types("a = 1\nb = 2\n"), types("a = 1\r\nb = 2\r\n"));
    }

    @Test
    void tabInIndentationIsAnError() {
        LexerException e = assertThrows(LexerException.class, () -> Lexer.tokenize("if a:\n\tpass\n"));
        assertEquals(2, e.line());
    }

    @Test
    void inconsistentDedentIsAnError() {
        String src = "if a:\n        x = 1\n    y = 2\n";
        LexerException e = assertThrows(LexerException.class, () -> Lexer.tokenize(src));
        assertEquals(3, e.line());
    }

    // ---------------- Nombres ----------------

    @Test
    void numbers() {
        List<Token> t = Lexer.tokenize("1_000 0xFF 0b101 3.14 1e3 2.5E-2 7\n");
        assertEquals(INT, t.get(0).type());
        assertEquals("1000", t.get(0).text());
        assertEquals(INT, t.get(1).type());
        assertEquals("0xFF", t.get(1).text());
        assertEquals(INT, t.get(2).type());
        assertEquals("0b101", t.get(2).text());
        assertEquals(FLOAT, t.get(3).type());
        assertEquals(FLOAT, t.get(4).type());
        assertEquals("2.5E-2", t.get(5).text());
        assertEquals(INT, t.get(6).type());
    }

    @Test
    void intFollowedByDotIsNotAFloat() {
        assertEquals(list(INT, DOT, NAME, NEWLINE, EOF), types("1.foo\n"));
    }

    @Test
    void invalidNumberLiteral() {
        assertThrows(LexerException.class, () -> Lexer.tokenize("12abc\n"));
    }

    // ---------------- Chaînes ----------------

    @Test
    void stringEscapesAreDecoded() {
        Token t = Lexer.tokenize("\"a\\nb\\t\\\"c\\\"\\u00e9\"\n").get(0);
        assertEquals(STRING, t.type());
        assertEquals("a\nb\t\"c\"é", t.text());
    }

    @Test
    void singleQuotedAndEmptyStrings() {
        List<Token> t = Lexer.tokenize("'ok' \"\" ''\n");
        assertEquals("ok", t.get(0).text());
        assertEquals("", t.get(1).text());
        assertEquals("", t.get(2).text());
    }

    @Test
    void tripleQuotedStringSpansLines() {
        List<Token> t = Lexer.tokenize("s = \"\"\"un\ndeux\"\"\"\nx = 1\n");
        assertEquals("un\ndeux", t.get(2).text());
        assertEquals(3, t.get(4).line());
    }

    @Test
    void unterminatedStringReportsItsStart() {
        LexerException e = assertThrows(LexerException.class, () -> Lexer.tokenize("x = 1\ny = \"abc\n"));
        assertEquals(2, e.line());
        assertEquals(5, e.column());
    }

    @Test
    void fstringKeepsRawContent() {
        Token t = Lexer.tokenize("f\"Aire : {f.aire():.2f} {{ok}}\"\n").get(0);
        assertEquals(FSTRING, t.type());
        assertEquals("Aire : {f.aire():.2f} {{ok}}", t.text());
    }

    @Test
    void fstringAcceptsOtherQuotesInsideBraces() {
        Token t = Lexer.tokenize("f\"{'ok' if x > 3 else 'ko'}\"\n").get(0);
        assertEquals(FSTRING, t.type());
        assertEquals("{'ok' if x > 3 else 'ko'}", t.text());
    }

    @Test
    void fstringUnclosedBraceIsAnError() {
        assertThrows(LexerException.class, () -> Lexer.tokenize("f\"{x\"\n"));
        assertThrows(LexerException.class, () -> Lexer.tokenize("f\"a}b\"\n"));
    }

    // ---------------- Opérateurs ----------------

    @Test
    void operators() {
        assertEquals(
                list(NAME, STARSTAR, NAME, ARROW, NAME, QDOT, NAME, PLUS_ASSIGN, NAME, EQ, NAME, NE,
                        NAME, LE, NAME, GE, NAME, PIPE, NAME, NEWLINE, EOF),
                types("a ** b -> c ?. d += e == f != g <= h >= i | j\n"));
    }

    @Test
    void elvisOperator() {
        assertEquals(list(NAME, ELVIS, INT, NEWLINE, EOF), types("a ?: 0\n"));
        assertEquals(list(NAME, QDOT, NAME, ELVIS, INT, NEWLINE, EOF), types("s?.length ?: 0\n"));
    }

    @Test
    void nullableTypeBeforeColonIsNotElvis() {
        assertEquals(
                list(DEF, NAME, LPAREN, RPAREN, ARROW, NAME, QUESTION, COLON, NEWLINE,
                        INDENT, PASS, NEWLINE, DEDENT, EOF),
                types("def f() -> Produit?:\n    pass\n"));
        assertEquals(
                list(NAME, COLON, NAME, QUESTION, ASSIGN, NULL, NEWLINE, EOF),
                types("x: str? = null\n"));
    }

    @Test
    void mismatchedBracketsAreErrors() {
        assertThrows(LexerException.class, () -> Lexer.tokenize("f(1]\n"));
        assertThrows(LexerException.class, () -> Lexer.tokenize("x = 1)\n"));
        LexerException e = assertThrows(LexerException.class, () -> Lexer.tokenize("a = 1\nf(1, 2\n"));
        assertEquals(2, e.line());
        assertEquals(2, e.column());
    }

    // ---------------- Mots-clés, noms, positions ----------------

    @Test
    void keywordsAndNames() {
        assertEquals(
                list(VAL, NAME, ASSIGN, TRUE, AND, NOT, FALSE, NEWLINE, EOF),
                types("val énergie_1 = true and not false\n"));
    }

    @Test
    void underscoreIsAName() {
        List<Token> t = Lexer.tokenize("case _:\n    pass\n");
        assertEquals(CASE, t.get(0).type());
        assertEquals(NAME, t.get(1).type());
        assertEquals("_", t.get(1).text());
    }

    @Test
    void positionsAreOneBased() {
        List<Token> t = Lexer.tokenize("ab = 1\n  # c\ncd = 2\n");
        assertEquals(1, t.get(0).line());
        assertEquals(1, t.get(0).column());
        assertEquals(6, t.get(2).column());
        Token cd = t.stream().filter(x -> x.text().equals("cd")).findFirst().orElseThrow();
        assertEquals(3, cd.line());
        assertEquals(1, cd.column());
    }

    @Test
    void unexpectedCharacter() {
        assertThrows(LexerException.class, () -> Lexer.tokenize("x = $\n"));
        assertThrows(LexerException.class, () -> Lexer.tokenize("x = !a\n"));
    }

    // ---------------- Les 20 exemples de la Phase 0 ----------------

    @Test
    void allPhase0ExamplesAreLexable() throws IOException {
        Path dir = Path.of("src/test/resources/exemples");
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.toString().endsWith(".dys")).sorted().collect(Collectors.toList());
        }
        assertEquals(21, files.size()); // 20 exemples, le n° 18 comporte deux fichiers
        for (Path p : files) {
            List<Token> tokens;
            try {
                tokens = Lexer.tokenize(Files.readString(p));
            } catch (LexerException e) {
                throw new AssertionError(p.getFileName() + " : " + e.getMessage(), e);
            }
            assertEquals(EOF, tokens.get(tokens.size() - 1).type(), p.getFileName().toString());
            long indents = tokens.stream().filter(t -> t.type() == INDENT).count();
            long dedents = tokens.stream().filter(t -> t.type() == DEDENT).count();
            assertEquals(indents, dedents, "INDENT/DEDENT déséquilibrés dans " + p.getFileName());
            assertTrue(tokens.size() > 3, p.getFileName().toString());
        }
    }
}
