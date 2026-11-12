package dev.dystos.app.ide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.dystos.app.language.CodeAnalysis;

/**
 * Coloration syntaxique du JavaScript.
 *
 * Contrainte du bloc de code : ne jamais lever d'exception sur du code à
 * moitié écrit. Ce scanner est volontairement simple — il colore, il ne
 * compile pas. La détection d'erreurs reste assurée par le moteur à
 * l'exécution.
 */
final class JavaScriptLexer {

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "var", "let", "const", "function", "return", "if", "else", "for",
            "while", "do", "switch", "case", "default", "break", "continue",
            "new", "typeof", "instanceof", "in", "of", "class", "extends",
            "super", "this", "try", "catch", "finally", "throw", "async",
            "await", "yield", "import", "export", "delete", "void"));

    private static final Set<String> LITERALS = new HashSet<>(Arrays.asList(
            "true", "false", "null", "undefined", "NaN", "Infinity"));

    private static final Set<String> OPERATORS2 = new HashSet<>(Arrays.asList(
            "=>", "==", "===", "!=", "!==", "<=", ">=", "&&", "||", "++",
            "--", "+=", "-=", "*=", "/=", "**", "??", "?.", "<<", ">>",
            "..."));

    private JavaScriptLexer() {
    }

    static CodeAnalysis.Result scan(String source) {
        String text = source == null ? "" : source;
        List<CodeAnalysis.Span> spans = new ArrayList<>();
        int i = 0;
        int n = text.length();

        while (i < n) {
            char c = text.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                int start = i;
                while (i < n && text.charAt(i) != '\n') {
                    i++;
                }
                spans.add(span(start, i, CodeAnalysis.Highlight.COMMENT));
                continue;
            }

            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int start = i;
                i += 2;
                while (i + 1 < n
                        && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(n, i + 2);
                spans.add(span(start, i, CodeAnalysis.Highlight.COMMENT));
                continue;
            }

            if (c == '"' || c == '\'' || c == '`') {
                int start = i;
                char quote = c;
                i++;
                while (i < n) {
                    char d = text.charAt(i);
                    if (d == '\\') {
                        i += 2;
                        continue;
                    }
                    i++;
                    if (d == quote) {
                        break;
                    }
                }
                spans.add(span(start, Math.min(n, i), CodeAnalysis.Highlight.STRING));
                continue;
            }

            if (isIdentStart(c)) {
                int start = i;
                i++;
                while (i < n && isIdentPart(text.charAt(i))) {
                    i++;
                }
                String word = text.substring(start, i);
                CodeAnalysis.Highlight kind = KEYWORDS.contains(word)
                        ? CodeAnalysis.Highlight.KEYWORD
                        : (LITERALS.contains(word)
                        ? CodeAnalysis.Highlight.BUILTIN
                        : CodeAnalysis.Highlight.IDENTIFIER);
                spans.add(span(start, i, kind));
                continue;
            }

            if (Character.isDigit(c) || (c == '.'
                    && i + 1 < n && Character.isDigit(text.charAt(i + 1)))) {
                int start = i;
                i = endOfNumber(text, i);
                spans.add(span(start, i, CodeAnalysis.Highlight.NUMBER));
                continue;
            }

            int start = i;
            String pair = n - i >= 2 ? text.substring(i, i + 2) : "";
            i += OPERATORS2.contains(pair) ? 2 : 1;
            spans.add(span(start, i, CodeAnalysis.Highlight.OPERATOR));
        }

        return new CodeAnalysis.Result(spans, null, false);
    }

    private static int endOfNumber(String text, int i) {
        int n = text.length();
        char c = text.charAt(i);
        if (c == '0' && i + 1 < n) {
            char p = text.charAt(i + 1);
            if (p == 'x' || p == 'X' || p == 'b' || p == 'B'
                    || p == 'o' || p == 'O') {
                i += 2;
                while (i < n && isRadixDigit(text, p, text.charAt(i))) {
                    i++;
                }
                return i;
            }
        }
        i++;
        while (i < n && (Character.isDigit(text.charAt(i))
                || text.charAt(i) == '.' || text.charAt(i) == '_')) {
            i++;
        }
        if (i < n && (text.charAt(i) == 'e' || text.charAt(i) == 'E')) {
            i++;
            if (i < n && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
                i++;
            }
            while (i < n && Character.isDigit(text.charAt(i))) {
                i++;
            }
        }
        return i;
    }

    private static boolean isRadixDigit(String text, char prefix, char c) {
        return (prefix == 'x' || prefix == 'X')
                ? Character.digit(c, 16) >= 0 : Character.digit(c, 2) >= 0;
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static CodeAnalysis.Span span(int start, int end,
                                          CodeAnalysis.Highlight highlight) {
        return new CodeAnalysis.Span(start, end, highlight);
    }
}