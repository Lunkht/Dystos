package com.vulsoft.dystos.lexer;

import static com.vulsoft.dystos.lexer.Highlighter.Kind.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class HighlighterTest {

    private static List<String> spans(String src) {
        return Highlighter.highlight(src).stream()
                .map(s -> src.substring(s.start(), s.end()) + ":" + s.kind())
                .collect(Collectors.toList());
    }

    private static void assertValid(String src) {
        int prevEnd = 0;
        for (Highlighter.Span s : Highlighter.highlight(src)) {
            assertTrue(s.start() >= prevEnd, "chevauchement ou désordre à " + s + " dans : " + src);
            assertTrue(s.end() > s.start() && s.end() <= src.length(), "span invalide " + s + " dans : " + src);
            prevEnd = s.end();
        }
    }

    @Test
    void keywordsConstantsSelfAndTypes() {
        assertEquals(List.of("def:KEYWORD", "main:FUNCTION", "(:PUNCTUATION", "):PUNCTUATION", "::PUNCTUATION"),
                spans("def main():"));
        assertEquals(List.of("val:KEYWORD"), spans("val x"));
        assertEquals(List.of("true:CONSTANT", "and:KEYWORD", "null:CONSTANT"), spans("true and null"));
        assertEquals(List.of("self:SELF", ".:PUNCTUATION"), spans("self."));
        assertEquals(List.of("list:TYPE", "[:PUNCTUATION", "int:TYPE", "]:PUNCTUATION"), spans("list[int]"));
    }

    @Test
    void classNamesConstantsAndCalls() {
        assertEquals(List.of("class:KEYWORD", "Compte:TYPE", "(:PUNCTUATION", "Base:TYPE", "):PUNCTUATION", "::PUNCTUATION"),
                spans("class Compte(Base):"));
        assertEquals(List.of("Jour:TYPE", ".:PUNCTUATION", "SAMEDI:CONSTANT"), spans("Jour.SAMEDI"));
        assertEquals(List.of("print:BUILTIN", "(:PUNCTUATION", "):PUNCTUATION"), spans("print()"));
        assertEquals(List.of("f:FUNCTION", "(:PUNCTUATION", "):PUNCTUATION"), spans("f()"));
        // un nom prédéfini n'est coloré comme tel que lorsqu'il est appelé
        assertTrue(spans("print = 1").stream().noneMatch(s -> s.contains("BUILTIN")));
    }

    @Test
    void numbersOperatorsCommentsDecorators() {
        assertEquals(List.of("1_000:NUMBER", "0xFF:NUMBER", "3.14:NUMBER", "1e-3:NUMBER", "2.5E+2:NUMBER"),
                spans("1_000 0xFF 3.14 1e-3 2.5E+2"));
        assertEquals(List.of("**:OPERATOR", "->:OPERATOR", "?.:OPERATOR", "?::OPERATOR", "+=:OPERATOR"),
                spans("** -> ?. ?: +="));
        assertEquals(List.of("# salut:COMMENT"), spans("# salut"));
        assertEquals(List.of("@test:DECORATOR", "@a.b:DECORATOR"), spans("@test\n@a.b"));
    }

    @Test
    void stringsAndEscapes() {
        assertEquals(List.of("\"a:STRING", "\\n:ESCAPE", "b\":STRING"), spans("\"a\\nb\""));
        assertEquals(List.of("'x':STRING"), spans("'x'"));
        assertEquals(List.of("\"\"\"a\nb\"\"\":STRING"), spans("\"\"\"a\nb\"\"\""));
        assertEquals(List.of("\":STRING", "\\u00e9:ESCAPE", "\":STRING"), spans("\"\\u00e9\""));
    }

    @Test
    void fstringInterpolation() {
        assertEquals(List.of("f\"Aire :STRING", "{:PUNCTUATION", "f:FUNCTION", "(:PUNCTUATION", "):PUNCTUATION",
                        "::OPERATOR", ".2f:STRING", "}:PUNCTUATION", "\":STRING"),
                spans("f\"Aire {f():.2f}\""));
        assertEquals(List.of("f\"{{ok}}\":STRING"), spans("f\"{{ok}}\""));
        List<String> nested = spans("f\"{'a' if x else 'b'}\"");
        assertTrue(nested.contains("if:KEYWORD") && nested.contains("else:KEYWORD") && nested.contains("'a':STRING"),
                nested.toString());
    }

    @Test
    void elvisInFstringIsNotAFormatSeparator() {
        List<String> s = spans("f\"{a ?: 0}\"");
        assertTrue(s.contains("?::OPERATOR") && s.contains("0:NUMBER"), s.toString());
        assertTrue(s.stream().noneMatch(x -> x.endsWith(":STRING") && x.contains(" 0")), s.toString());
    }

    @Test
    void toleratesBrokenInput() {
        for (String src : List.of("\"abc", "f\"{x", "f\"{", "\"\"\"abc", "@", "def", "0x", "1e", "f", "$$$", "x = 'a\nb = 2",
                "f\"{'a}\"", "\\", "#", "((((")) {
            assertValid(src);
        }
        // une chaîne non terminée s'arrête en fin de ligne
        assertEquals(List.of("'a:STRING", "=:OPERATOR", "2:NUMBER"), spans("'a\n= 2"));
    }

    @Test
    void randomInputNeverBreaksTheInvariants() {
        Random r = new Random(42);
        String alphabet = "abc_ 0129\n\t\"'{}()[]:.#@\\fF?-+*/%<>=!|,éÉ";
        for (int n = 0; n < 3000; n++) {
            StringBuilder sb = new StringBuilder();
            int len = r.nextInt(60);
            for (int i = 0; i < len; i++) {
                sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
            }
            assertValid(sb.toString());
        }
    }

    @Test
    void allPhase0ExamplesAreHighlightedConsistently() throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.list(Path.of("src/test/resources/exemples"))) {
            files = s.filter(p -> p.toString().endsWith(".dys")).sorted().collect(Collectors.toList());
        }
        assertEquals(21, files.size());
        for (Path p : files) {
            String src = Files.readString(p);
            assertValid(src);
            long kw = Highlighter.highlight(src).stream().filter(x -> x.kind() == KEYWORD).count();
            assertTrue(kw > 0, p.getFileName().toString());
            // aucun identifiant du lexer ne doit être un mot-clé non colorié
            for (Token t : Lexer.tokenize(src)) {
                if (t.type().ordinal() >= TokenType.DEF.ordinal() && t.type().ordinal() <= TokenType.IS.ordinal()) {
                    assertTrue(Highlighter.KEYWORDS.contains(t.text()) || Highlighter.CONSTANTS.contains(t.text()),
                            "mot-clé inconnu du colorateur : " + t.text());
                }
            }
        }
    }

    @Test
    void everyKeywordIsReallyAKeywordForTheLexer() {
        for (String w : Highlighter.KEYWORDS) {
            assertNotEquals(TokenType.NAME, Lexer.tokenize(w).get(0).type(), w);
        }
        for (String w : Highlighter.CONSTANTS) {
            assertNotEquals(TokenType.NAME, Lexer.tokenize(w).get(0).type(), w);
        }
    }
}
