package dev.dystos.app.language;

import java.util.ArrayList;
import java.util.List;

/**
 * Chaîne formatée {@code f"..."}.
 *
 * Le texte est découpé en fragments littéraux et en expressions placées entre
 * accolades. Les accolades échappées {@code &#123;&#125;} produisent une accolade
 * littérale.
 */
public final class FormattedString implements Expression {

    /** Fragment d'une chaîne formatée : littéral ou expression. */
    public static final class Part {
        private final String literal;
        private final Expression expression;

        private Part(String literal, Expression expression) {
            this.literal = literal;
            this.expression = expression;
        }

        static Part literal(String text) {
            return new Part(text, null);
        }

        static Part expression(Expression expression) {
            return new Part(null, expression);
        }

        public boolean isLiteral() {
            return literal != null;
        }

        public String literal() {
            return literal;
        }

        public Expression expression() {
            return expression;
        }
    }

    private final String raw;
    private final int line;

    public FormattedString(String raw, int line) {
        this.raw = raw;
        this.line = line;
    }

    /** Découpe le texte brut en fragments, en analysant chaque expression. */
    public List<Part> parts() {
        List<Part> parts = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int index = 0;

        while (index < raw.length()) {
            char c = raw.charAt(index);

            if (c == '{' && index + 1 < raw.length() && raw.charAt(index + 1) == '{') {
                literal.append('{');
                index += 2;
                continue;
            }
            if (c == '}' && index + 1 < raw.length() && raw.charAt(index + 1) == '}') {
                literal.append('}');
                index += 2;
                continue;
            }
            if (c == '{') {
                int depth = 1;
                int start = index + 1;
                int end = start;
                while (end < raw.length() && depth > 0) {
                    char inner = raw.charAt(end);
                    if (inner == '{') {
                        depth++;
                    } else if (inner == '}') {
                        depth--;
                        if (depth == 0) {
                            break;
                        }
                    } else if (inner == '"' || inner == '\'') {
                        end = skipString(raw, end);
                        continue;
                    }
                    end++;
                }
                if (depth != 0) {
                    throw new DystosException("accolade non fermée dans la chaîne", line);
                }
                if (literal.length() > 0) {
                    parts.add(Part.literal(literal.toString()));
                    literal.setLength(0);
                }
                String inner = raw.substring(start, end);
                parts.add(Part.expression(Parser.parseExpression(inner, line)));
                index = end + 1;
                continue;
            }

            literal.append(c);
            index++;
        }

        if (literal.length() > 0) {
            parts.add(Part.literal(literal.toString()));
        }
        return parts;
    }

    /** Avance au-delà d'une chaîne imbriquée dans une expression. */
    private static int skipString(String text, int start) {
        char quote = text.charAt(start);
        int index = start + 1;
        while (index < text.length() && text.charAt(index) != quote) {
            if (text.charAt(index) == '\\') {
                index++;
            }
            index++;
        }
        return Math.min(index, text.length() - 1);
    }

    @Override
    public int line() {
        return line;
    }
}