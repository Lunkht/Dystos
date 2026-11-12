package dev.dystos.app.language;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Coloration syntaxique et détection d'erreurs pour l'éditeur.
 *
 * L'éditeur ne doit pas planter pendant que l'apprenant tape : un programme à
 * moitié écrit est forcément invalide. Cette classe ne lève donc jamais
 * d'exception. Elle renvoie ce qu'elle a pu analyser, et signale la première
 * erreur rencontrée.
 */
public final class CodeAnalysis {

    /** Catégories affichées par l'éditeur, indépendantes du lexer. */
    public enum Highlight {
        PLAIN,
        KEYWORD,
        STRING,
        NUMBER,
        OPERATOR,
        BUILTIN,
        IDENTIFIER,
        COMMENT
    }

    /** Un fragment de texte à colorer. */
    public static final class Span {
        private final int start;
        private final int end;
        private final Highlight highlight;

        public Span(int start, int end, Highlight highlight) {
            this.start = start;
            this.end = end;
            this.highlight = highlight;
        }

        public int start() {
            return start;
        }

        public int end() {
            return end;
        }

        public Highlight highlight() {
            return highlight;
        }

        public int length() {
            return Math.max(0, end - start);
        }
    }

    /** Erreur de lecture ou d'analyse, avec sa ligne. */
    public static final class Problem {
        private final int line;
        private final String message;

        public Problem(int line, String message) {
            this.line = line;
            this.message = message;
        }

        public int line() {
            return line;
        }

        public String message() {
            return message;
        }
    }

    /**
     * Résultat complet d'une analyse.
     *
     * La coloration est toujours présente, même en cas d'erreur : l'apprenant
     * voit son code coloré et, en plus, l'endroit où le lexer s'est arrêté.
     */
    public static final class Result {
        private final List<Span> spans;
        private final Problem problem;
        private final boolean syntaxError;

        public Result(List<Span> spans, Problem problem, boolean syntaxError) {
            this.spans = spans;
            this.problem = problem;
            this.syntaxError = syntaxError;
        }

        public List<Span> spans() {
            return spans;
        }

        /** Première erreur trouvée, ou null si le code est analysable. */
        public Problem problem() {
            return problem;
        }

        public boolean hasProblem() {
            return problem != null;
        }

        /** Vrai pour une erreur de parsing, faux pour une erreur de lexer. */
        public boolean isSyntaxError() {
            return syntaxError;
        }
    }

    private static final Map<String, Boolean> KEYWORDS = new HashMap<>();
    private static final Map<String, Boolean> BUILTINS = new HashMap<>();

    static {
        for (String keyword : Arrays.asList(
                "val", "var", "def", "return", "if", "elif", "else", "for", "in",
                "while", "and", "or", "not", "true", "false", "break", "continue",
                "class", "interface", "enum", "abstract", "final", "import",
                "package", "match", "case", "pass", "try", "except", "finally",
                "raise", "assert", "with", "as", "is", "null")) {
            KEYWORDS.put(keyword, Boolean.TRUE);
        }
        for (String name : Arrays.asList("print", "range", "len", "str", "int", "float",
                "bool", "abs", "min", "max", "round")) {
            BUILTINS.put(name, Boolean.TRUE);
        }
    }

    private CodeAnalysis() {
    }

    /** Les mots-clés, pour l'écran de référence. */
    public static List<String> keywords() {
        return new ArrayList<>(KEYWORDS.keySet());
    }

    /** Les fonctions intégrées, pour l'écran de référence. */
    public static List<String> builtins() {
        return new ArrayList<>(BUILTINS.keySet());
    }

    /**
     * Colore et vérifie un programme sans jamais lever d'exception.
     *
     * @param source code en cours de saisie
     */
    public static Result analyse(String source) {
        String safe = source == null ? "" : source;

        Map<Integer, Integer> lineStarts = lineStarts(safe);
        List<Span> spans = new ArrayList<>();
        addCommentSpans(safe, spans);

        Problem problem = null;
        boolean syntaxError = false;

        Lexer.Lexed lexed = Lexer.partialTokenize(safe);
        for (Token token : lexed.tokens) {
            int start = offsetOf(lineStarts, safe, token.line(), token.column());
            int end = endOfToken(safe, start, token);
            if (end > start && end <= safe.length()) {
                spans.add(new Span(start, end, highlightOf(token)));
            }
        }

        if (lexed.error != null) {
            problem = new Problem(lexed.error.getLine(), lexed.error.getMessage());
        } else if (!safe.trim().isEmpty()) {
            try {
                Parser.parse(safe);
            } catch (DystosException e) {
                problem = new Problem(e.getLine(), e.getMessage());
                syntaxError = true;
            }
        }

        spans.sort((left, right) -> Integer.compare(left.start, right.start));
        return new Result(spans, problem, syntaxError);
    }

    private static Highlight highlightOf(Token token) {
        switch (token.kind()) {
            case KEYWORD:
                return Highlight.KEYWORD;
            case STRING:
            case FORMATTED_STRING:
                return Highlight.STRING;
            case INT:
            case FLOAT:
                return Highlight.NUMBER;
            case OPERATOR:
                return Highlight.OPERATOR;
            case IDENT:
                return BUILTINS.containsKey(token.text())
                        ? Highlight.BUILTIN : Highlight.IDENTIFIER;
            default:
                return Highlight.PLAIN;
        }
    }

    /**
     * Position absolue de fin du jeton dans le source.
     *
     * Le lexer normalise certaines formes : une chaîne perd ses guillemets et
     * ses échappements, un nombre perd ses séparateurs. On relit donc le source
     * à partir de la position du jeton pour colorer exactement ce qui est
     * affiché, préfixe `f` et guillemets compris.
     */
    private static int endOfToken(String source, int start, Token token) {
        if (start >= source.length()) {
            return start;
        }

        switch (token.kind()) {
            case STRING:
            case FORMATTED_STRING:
                return endOfString(source, start);
            case INT:
            case FLOAT:
                return endOfNumber(source, start);
            default:
                return Math.min(start + token.text().length(), source.length());
        }
    }

    private static int endOfString(String source, int start) {
        int i = start;
        // Préfixe `f` d'une chaîne formatée.
        if (source.charAt(i) == 'f' || source.charAt(i) == 'F') {
            i++;
        }
        if (i >= source.length()) {
            return source.length();
        }

        char quote = source.charAt(i);
        if (quote != '"' && quote != '\'') {
            return Math.min(start + 1, source.length());
        }
        i++;

        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                return i + 1;
            }
            if (c == '\n') {
                return i;
            }
            i++;
        }
        return source.length();
    }

    private static int endOfNumber(String source, int start) {
        int i = start;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (Character.isDigit(c) || c == '_') {
                i++;
            } else if (c == '.'
                    && i + 1 < source.length() && Character.isDigit(source.charAt(i + 1))) {
                i += 2;
            } else if ((c == 'e' || c == 'E') && isExponentAt(source, i)) {
                i = endOfExponent(source, i);
            } else {
                break;
            }
        }
        return i;
    }

    private static boolean isExponentAt(String source, int at) {
        int next = at + 1;
        if (next < source.length()
                && (source.charAt(next) == '+' || source.charAt(next) == '-')) {
            next++;
        }
        return next < source.length() && Character.isDigit(source.charAt(next));
    }

    private static int endOfExponent(String source, int at) {
        int i = at + 1;
        if (i < source.length()
                && (source.charAt(i) == '+' || source.charAt(i) == '-')) {
            i++;
        }
        while (i < source.length() && Character.isDigit(source.charAt(i))) {
            i++;
        }
        return i;
    }

    private static Map<Integer, Integer> lineStarts(String source) {
        Map<Integer, Integer> starts = new HashMap<>();
        starts.put(1, 0);
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                starts.put(lineOf(source, i + 1), i + 1);
            }
        }
        return starts;
    }

    private static int lineOf(String source, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static int offsetOf(Map<Integer, Integer> starts, String source,
                                int line, int column) {
        Integer start = starts.get(line);
        if (start == null) {
            return Math.min(column, source.length());
        }
        return Math.min(start + column - 1, source.length());
    }

    /** Les commentaires sont ignorés par le lexer : on les repère à part. */
    private static void addCommentSpans(String source, List<Span> spans) {
        boolean inString = false;
        char quote = '"';
        int commentStart = -1;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);

            if (commentStart >= 0) {
                if (c == '\n') {
                    spans.add(new Span(commentStart, i, Highlight.COMMENT));
                    commentStart = -1;
                }
                continue;
            }

            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    inString = false;
                }
                continue;
            }

            if (c == '"' || c == '\'') {
                inString = true;
                quote = c;
            } else if (c == '#') {
                commentStart = i;
            }
        }

        if (commentStart >= 0) {
            spans.add(new Span(commentStart, source.length(), Highlight.COMMENT));
        }
    }
}