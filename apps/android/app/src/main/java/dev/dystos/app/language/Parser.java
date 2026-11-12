package dev.dystos.app.language;

import java.util.List;

/**
 * Analyseur syntaxique : transforme le jeton en arbre syntaxique.
 *
 * L'arbre est volontairement simple : une liste de déclarations au niveau
 * supérieur, chaque déclaration portant sa ligne pour les messages d'erreur.
 */
public final class Parser {

    private final List<Token> tokens;
    private int index;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /** Analyse un programme complet. */
    public static List<Statement> parse(String source) {
        return new Parser(Lexer.tokenize(source)).parseProgram();
    }

    /**
     * Analyse une expression isolée, utilisée pour le contenu d'une chaîne
     * formatée. La ligne fournie est celle de la chaîne entière.
     */
    public static Expression parseExpression(String source, int line) {
        return new Parser(Lexer.tokenize(source)).parseSingleExpression(line);
    }

    private Expression parseSingleExpression(int line) {
        Expression expression = parseExpression();
        Token rest = peek();
        if (rest.kind() != TokenKind.EOF && rest.kind() != TokenKind.NEWLINE) {
            throw new DystosException(
                    "expression invalide près de '" + rest.text() + "'", line);
        }
        return expression;
    }

    private List<Statement> parseProgram() {
        java.util.List<Statement> program = new java.util.ArrayList<>();
        skipSeparators();
        while (!isAtEnd()) {
            program.addAll(parseBlock(0));
            skipSeparators();
        }
        return program;
    }

    /** Analyse des instructions jusqu'à DEDENT ou EOF. */
    private java.util.List<Statement> parseBlock(int depth) {
        java.util.List<Statement> statements = new java.util.ArrayList<>();
        skipSeparators();

        if (depth > 0 && !check(TokenKind.INDENT)) {
            throw new DystosException("bloc attendu après ':'", peek().line());
        }
        if (check(TokenKind.INDENT)) {
            advance();
        }

        while (!check(TokenKind.DEDENT) && !isAtEnd()) {
            if (check(TokenKind.NEWLINE)) {
                advance();
                continue;
            }
            statements.addAll(parseStatement());
        }

        if (check(TokenKind.DEDENT)) {
            advance();
        }
        return statements;
    }

    private java.util.List<Statement> parseStatement() {
        Token token = peek();

        if (token.kind() == TokenKind.KEYWORD) {
            switch (token.text()) {
                case "def":
                    return java.util.Collections.singletonList(parseFunction());
                case "if":
                    return java.util.Collections.singletonList(parseIf());
                case "for":
                    return java.util.Collections.singletonList(parseFor());
                case "while":
                    return java.util.Collections.singletonList(parseWhile());
                default:
                    break;
            }
        }

        Statement statement = parseSimpleStatement();
        java.util.List<Statement> result = new java.util.ArrayList<>();
        result.add(statement);
        return result;
    }

    private FunctionDeclaration parseFunction() {
        Token keyword = advance();
        Token name = expect(TokenKind.IDENT, "nom de fonction attendu après 'def'");
        expectOperator("(");
        java.util.List<Parameter> parameters = parseParameters();
        expectOperator(")");

        String returnType = null;
        if (matchOperator("->")) {
            returnType = parseTypeName();
        }

        expectOperator(":");
        java.util.List<Statement> body = parseBlock(1);
        return new FunctionDeclaration(name.text(), parameters, returnType, body, keyword.line());
    }

    private java.util.List<Parameter> parseParameters() {
        java.util.List<Parameter> parameters = new java.util.ArrayList<>();
        if (checkOperator(")")) {
            return parameters;
        }
        do {
            Token name = expect(TokenKind.IDENT, "nom de paramètre attendu");
            String type = null;
            if (matchOperator(":")) {
                type = parseTypeName();
            }
            Expression defaultValue = null;
            if (matchOperator("=")) {
                defaultValue = parseExpression();
            }
            parameters.add(new Parameter(name.text(), type, defaultValue));
        } while (matchOperator(","));
        return parameters;
    }

    private IfStatement parseIf() {
        Token keyword = advance();
        Condition condition = parseCondition();
        expectOperator(":");
        java.util.List<Statement> thenBranch = parseBlock(1);

        java.util.List<IfStatement.Branch> elifBranches = new java.util.ArrayList<>();
        skipSeparators();

        while (checkKeyword("elif")) {
            advance();
            Condition elifCondition = parseCondition();
            expectOperator(":");
            elifBranches.add(new IfStatement.Branch(elifCondition, parseBlock(1)));
            skipSeparators();
        }

        java.util.List<Statement> elseBranch = null;
        if (checkKeyword("else")) {
            advance();
            expectOperator(":");
            elseBranch = parseBlock(1);
        }
        return new IfStatement(condition, thenBranch, elifBranches, elseBranch, keyword.line());
    }

    private ForStatement parseFor() {
        Token keyword = advance();
        Token name = expect(TokenKind.IDENT, "nom de variable attendu après 'for'");
        if (!checkKeyword("in")) {
            throw new DystosException("'in' attendu dans la boucle 'for'", peek().line());
        }
        advance();
        Expression iterable = parseExpression();
        expectOperator(":");
        java.util.List<Statement> body = parseBlock(1);
        return new ForStatement(name.text(), iterable, body, keyword.line());
    }

    private WhileStatement parseWhile() {
        Token keyword = advance();
        Condition condition = parseCondition();
        expectOperator(":");
        java.util.List<Statement> body = parseBlock(1);
        return new WhileStatement(condition, body, keyword.line());
    }

    private Statement parseSimpleStatement() {
        Token token = peek();

        if (token.kind() == TokenKind.KEYWORD) {
            switch (token.text()) {
                case "val":
                case "var":
                    return parseDeclaration();
                case "return":
                    return parseReturn();
                case "break":
                    advance();
                    return new BreakStatement(token.line());
                case "continue":
                    advance();
                    return new ContinueStatement(token.line());
                default:
                    break;
            }
        }

        if (token.kind() == TokenKind.IDENT) {
            // Forme courte : `nom: type = valeur` déclare une constante.
            if (isShortDeclarationAhead()) {
                return parseShortDeclaration();
            }
            if (isAssignmentAhead()) {
                return parseAssignment();
            }
        }
        return parseExpressionStatement();
    }

    /** Vrai si les jetons suivants forment {@code nom : type ...}. */
    private boolean isShortDeclarationAhead() {
        return index + 1 < tokens.size()
                && tokens.get(index + 1).kind() == TokenKind.OPERATOR
                && ":".equals(tokens.get(index + 1).text());
    }

    /**
     * Déclaration sans {@code val} ni {@code var} : la valeur est immuable.
     * C'est la forme courte du langage, celle des exemples.
     */
    private Statement parseShortDeclaration() {
        Token name = advance();
        expectOperator(":");
        String type = parseTypeName();
        expectOperator("=");
        Expression value = parseExpression();
        expectEndOfStatement();
        return new VarDeclaration(name.text(), type, false, value, name.line());
    }

    /** Vrai si les jetons suivants forment {@code nom = ...} ou {@code nom op= ...}. */
    private boolean isAssignmentAhead() {
        int probe = index + 1;
        if (probe >= tokens.size()) {
            return false;
        }
        Token next = tokens.get(probe);
        if (next.kind() != TokenKind.OPERATOR) {
            return false;
        }
        String text = next.text();
        return "=".equals(text) || "+=".equals(text) || "-=".equals(text)
                || "*=".equals(text) || "/=".equals(text) || "%=".equals(text);
    }

    private Statement parseDeclaration() {
        Token keyword = advance();
        Token name = expect(TokenKind.IDENT, "nom de variable attendu");
        String type = null;
        if (matchOperator(":")) {
            type = parseTypeName();
        }
        expectOperator("=");
        Expression value = parseExpression();
        expectEndOfStatement();
        boolean mutable = "var".equals(keyword.text());
        return new VarDeclaration(name.text(), type, mutable, value, keyword.line());
    }

    private Statement parseAssignment() {
        Token name = advance();
        Token operator = advance();
        Expression value = parseExpression();
        expectEndOfStatement();
        String op = operator.text();
        if ("=".equals(op)) {
            return new Assignment(name.text(), null, value, name.line());
        }
        return new Assignment(name.text(), op.substring(0, op.length() - 1), value, name.line());
    }

    private Statement parseReturn() {
        Token keyword = advance();
        if (check(TokenKind.NEWLINE) || check(TokenKind.DEDENT) || isAtEnd()) {
            return new ReturnStatement(null, keyword.line());
        }
        Expression value = parseExpression();
        expectEndOfStatement();
        return new ReturnStatement(value, keyword.line());
    }

    private Statement parseExpressionStatement() {
        Expression expression = parseExpression();
        expectEndOfStatement();
        return new ExpressionStatement(expression);
    }

    private Condition parseCondition() {
        return new Condition(parseExpression());
    }

    /** Type simple : un nom, éventuellement générique. */
    private String parseTypeName() {
        Token name = expect(TokenKind.IDENT, "nom de type attendu");
        StringBuilder text = new StringBuilder(name.text());
        if (matchOperator("<")) {
            text.append('<');
            while (!checkOperator(">") && !isAtEnd()) {
                Token part = advance();
                text.append(part.text());
            }
            expectOperator(">");
            text.append('>');
        }
        return text.toString();
    }

    private void expectEndOfStatement() {
        if (check(TokenKind.NEWLINE) || check(TokenKind.DEDENT) || isAtEnd()) {
            return;
        }
        throw new DystosException(
                "fin d'instruction inattendue près de '" + peek().text() + "'", peek().line());
    }

    private void skipSeparators() {
        while (check(TokenKind.NEWLINE)) {
            advance();
        }
    }

    // ---------------------------------------------------------------- expressions

    Expression parseExpression() {
        return parseOr();
    }

    private Expression parseOr() {
        Expression left = parseAnd();
        while (checkKeyword("or")) {
            int line = advance().line();
            left = new LogicalExpression(left, "or", parseAnd(), line);
        }
        return left;
    }

    private Expression parseAnd() {
        Expression left = parseEquality();
        while (checkKeyword("and")) {
            int line = advance().line();
            left = new LogicalExpression(left, "and", parseEquality(), line);
        }
        return left;
    }

    private Expression parseEquality() {
        Expression left = parseComparison();
        while (checkOperator("==") || checkOperator("!=")) {
            Token operator = advance();
            left = new BinaryExpression(left, operator.text(), parseComparison(), operator.line());
        }
        return left;
    }

    private Expression parseComparison() {
        Expression left = parseTerm();
        while (checkOperator("<") || checkOperator("<=")
                || checkOperator(">") || checkOperator(">=")) {
            Token operator = advance();
            left = new BinaryExpression(left, operator.text(), parseTerm(), operator.line());
        }
        return left;
    }

    private Expression parseTerm() {
        Expression left = parseFactor();
        while (checkOperator("+") || checkOperator("-")) {
            Token operator = advance();
            left = new BinaryExpression(left, operator.text(), parseFactor(), operator.line());
        }
        return left;
    }

    private Expression parseFactor() {
        Expression left = parseUnary();
        while (checkOperator("*") || checkOperator("/") || checkOperator("%")) {
            Token operator = advance();
            left = new BinaryExpression(left, operator.text(), parseUnary(), operator.line());
        }
        return left;
    }

    private Expression parseUnary() {
        if (checkOperator("-")) {
            Token operator = advance();
            return new UnaryExpression("-", parseUnary(), operator.line());
        }
        if (checkOperator("+")) {
            advance();
            return parseUnary();
        }
        if (checkKeyword("not")) {
            Token operator = advance();
            return new UnaryExpression("not", parseUnary(), operator.line());
        }
        return parsePostfix();
    }

    private Expression parsePostfix() {
        Expression expression = parsePrimary();
        while (checkOperator("(")) {
            Token open = advance();
            java.util.List<Expression> arguments = new java.util.ArrayList<>();
            java.util.List<String> argumentNames = new java.util.ArrayList<>();
            if (!checkOperator(")")) {
                do {
                    // Un argument nommé s'écrit `nom: valeur`.
                    if (peek().kind() == TokenKind.IDENT && isNamedArgumentAhead()) {
                        argumentNames.add(advance().text());
                        expectOperator(":");
                        arguments.add(parseExpression());
                    } else {
                        argumentNames.add(null);
                        arguments.add(parseExpression());
                    }
                } while (matchOperator(","));
            }
            expectOperator(")");
            if (expression instanceof Variable) {
                expression = new Call(((Variable) expression).name(),
                        arguments, argumentNames, open.line());
            } else {
                expression = new CallExpression(expression, arguments, open.line());
            }
        }
        return expression;
    }

    /** Vrai si le jeton actuel commence un argument nommé suivi de ':'. */
    private boolean isNamedArgumentAhead() {
        return index + 1 < tokens.size()
                && tokens.get(index + 1).kind() == TokenKind.OPERATOR
                && ":".equals(tokens.get(index + 1).text());
    }

    private Expression parsePrimary() {
        Token token = peek();

        switch (token.kind()) {
            case INT:
                advance();
                return new Literal(parseNumber(token.text(), false), token.line());
            case FLOAT:
                advance();
                return new Literal(parseNumber(token.text(), true), token.line());
            case STRING:
                advance();
                return new Literal(token.text(), token.line());
            case FORMATTED_STRING:
                advance();
                return new FormattedString(token.text(), token.line());
            case KEYWORD:
                if ("true".equals(token.text()) || "false".equals(token.text())) {
                    advance();
                    return new Literal("true".equals(token.text()), token.line());
                }
                break;
            case IDENT:
                advance();
                return new Variable(token.text(), token.line());
            case OPERATOR:
                if ("(".equals(token.text())) {
                    advance();
                    Expression inner = parseExpression();
                    expectOperator(")");
                    return inner;
                }
                if ("[".equals(token.text())) {
                    return parseListLiteral();
                }
                break;
            default:
                break;
        }

        throw new DystosException(
                "expression inattendue près de '" + token.text() + "'", token.line());
    }

    private Expression parseListLiteral() {
        Token open = expectOperator("[");
        java.util.List<Expression> items = new java.util.ArrayList<>();
        if (!checkOperator("]")) {
            do {
                items.add(parseExpression());
            } while (matchOperator(","));
        }
        expectOperator("]");
        return new ListLiteral(items, open.line());
    }

    private Object parseNumber(String text, boolean isFloat) {
        try {
            if (isFloat) {
                return Double.parseDouble(text.replace("_", ""));
            }
            return Long.parseLong(text.replace("_", ""));
        } catch (NumberFormatException e) {
            throw new DystosException("littéral numérique invalide : " + text);
        }
    }

    // ---------------------------------------------------------------- jetons

    private Token expect(TokenKind kind, String message) {
        if (check(kind)) {
            return advance();
        }
        throw new DystosException(message + " (reçu '" + peek().text() + "')", peek().line());
    }

    private Token expectOperator(String text) {
        if (checkOperator(text)) {
            return advance();
        }
        throw new DystosException(
                "'" + text + "' attendu (reçu '" + peek().text() + "')", peek().line());
    }

    private boolean matchOperator(String text) {
        if (checkOperator(text)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean checkOperator(String text) {
        Token token = peek();
        return token.kind() == TokenKind.OPERATOR && token.text().equals(text);
    }

    private boolean checkKeyword(String text) {
        Token token = peek();
        return token.kind() == TokenKind.KEYWORD && token.text().equals(text);
    }

    private boolean check(TokenKind kind) {
        return peek().kind() == kind;
    }

    private boolean isAtEnd() {
        return peek().kind() == TokenKind.EOF;
    }

    private Token peek() {
        return tokens.get(index);
    }

    private Token advance() {
        Token current = peek();
        if (!isAtEnd()) {
            index++;
        }
        return current;
    }
}