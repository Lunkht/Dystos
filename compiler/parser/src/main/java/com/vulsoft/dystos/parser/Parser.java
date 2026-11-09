package com.vulsoft.dystos.parser;

import static com.vulsoft.dystos.lexer.TokenType.*;

import com.vulsoft.dystos.ast.Ast.*;
import com.vulsoft.dystos.lexer.Lexer;
import com.vulsoft.dystos.lexer.LexerException;
import com.vulsoft.dystos.lexer.Token;
import com.vulsoft.dystos.lexer.TokenType;
import java.util.ArrayList;
import java.util.List;

/**
 * Analyseur syntaxique de Dystos (descente récursive).
 *
 * <p>Précédence des expressions, du plus faible au plus fort :
 * lambda, {@code a if c else b}, {@code or}, {@code and}, {@code not}, comparaisons
 * ({@code == != < > <= >= in, not in, is, is not}), {@code ?:}, {@code + -}, {@code * / %},
 * unaires {@code - +}, {@code **} (associatif à droite), puis suffixes
 * ({@code .x}, {@code ?.x}, {@code (...)}, {@code [...]}).
 */
public final class Parser {

    private final List<Token> tokens;
    private int p = 0;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // ------------------------------------------------------------------
    // Points d'entrée
    // ------------------------------------------------------------------

    /** Analyse un fichier source complet. */
    public static SourceFile parse(String source) {
        return new Parser(Lexer.tokenize(source)).sourceFile();
    }

    /** Analyse une expression seule (utile pour les tests et les f-strings). */
    public static Expr parseExpression(String source) {
        Parser ps = new Parser(Lexer.tokenize(source.strip() + "\n"));
        Expr e = ps.expr();
        ps.expect(NEWLINE, "la fin de l'expression");
        ps.expect(EOF, "la fin de l'expression");
        return e;
    }

    // ------------------------------------------------------------------
    // Fichier, imports, déclarations
    // ------------------------------------------------------------------

    private SourceFile sourceFile() {
        Pos start = pos(peek());
        String pkg = null;
        if (check(PACKAGE)) {
            advance();
            pkg = dottedName("un nom de package");
            endOfLine();
        }
        List<ImportDecl> imports = new ArrayList<>();
        while (check(IMPORT)) {
            imports.add(importDecl());
        }
        List<TopLevel> decls = new ArrayList<>();
        List<Stmt> statements = new ArrayList<>();
        while (!check(EOF)) {
            if (startsDeclaration()) {
                decls.add(topLevel());
            } else {
                statements.add(statement());
            }
        }
        return new SourceFile(start, pkg, imports, decls, statements);
    }

    private boolean startsDeclaration() {
        switch (peek().type()) {
            case AT:
            case ABSTRACT:
            case FINAL:
            case DEF:
            case CLASS:
            case INTERFACE:
            case ENUM:
                return true;
            default:
                return false;
        }
    }

    private ImportDecl importDecl() {
        Token t = expect(IMPORT, "'import'");
        String path = dottedName("un chemin d'import");
        if (check(DOT) && checkAt(1, STAR)) {
            advance();
            advance();
            path += ".*";
        }
        String alias = null;
        if (match(AS)) {
            alias = expect(NAME, "un alias").text();
        }
        endOfLine();
        return new ImportDecl(pos(t), path, alias);
    }

    private String dottedName(String what) {
        StringBuilder sb = new StringBuilder(expect(NAME, what).text());
        while (check(DOT) && checkAt(1, NAME)) {
            advance();
            sb.append('.').append(advance().text());
        }
        return sb.toString();
    }

    private TopLevel topLevel() {
        List<Decorator> decorators = decorators();
        List<String> modifiers = modifiers();
        switch (peek().type()) {
            case DEF:
                return funcDecl(decorators, modifiers);
            case CLASS:
                return classDecl(decorators, modifiers);
            case INTERFACE:
                return interfaceDecl(decorators);
            case ENUM:
                return enumDecl(decorators);
            default:
                throw error(peek(), "déclaration attendue (def, class, interface ou enum) mais trouvé "
                        + describe(peek()));
        }
    }

    private List<Decorator> decorators() {
        List<Decorator> out = new ArrayList<>();
        while (check(AT)) {
            Token at = advance();
            String name = dottedName("un nom de décorateur");
            List<Arg> args = new ArrayList<>();
            if (match(LPAREN)) {
                args = callArgs();
            }
            endOfLine();
            out.add(new Decorator(pos(at), name, args));
        }
        return out;
    }

    private List<String> modifiers() {
        List<String> out = new ArrayList<>();
        while (check(ABSTRACT) || check(FINAL)) {
            out.add(advance().text());
        }
        return out;
    }

    private List<String> typeParams() {
        List<String> out = new ArrayList<>();
        if (match(LBRACKET)) {
            do {
                out.add(expect(NAME, "un paramètre de type").text());
            } while (match(COMMA));
            expect(RBRACKET, "']'");
        }
        return out;
    }

    private FuncDecl funcDecl(List<Decorator> decorators, List<String> modifiers) {
        Token t = expect(DEF, "'def'");
        String name = expect(NAME, "un nom de fonction").text();
        List<String> typeParams = typeParams();
        expect(LPAREN, "'('");
        List<Param> params = new ArrayList<>();
        while (!check(RPAREN)) {
            params.add(param());
            if (!match(COMMA)) {
                break;
            }
        }
        expect(RPAREN, "')'");
        TypeRef returnType = match(ARROW) ? type() : null;
        List<Stmt> body = null;
        if (check(COLON)) {
            body = suite();
        } else {
            endOfLine();
        }
        return new FuncDecl(pos(t), decorators, modifiers, name, typeParams, params, returnType, body);
    }

    private Param param() {
        Token n = expect(NAME, "un nom de paramètre");
        TypeRef type = match(COLON) ? type() : null;
        Expr def = match(ASSIGN) ? expr() : null;
        return new Param(pos(n), n.text(), type, def);
    }

    private ClassDecl classDecl(List<Decorator> decorators, List<String> modifiers) {
        Token t = expect(CLASS, "'class'");
        String name = expect(NAME, "un nom de classe").text();
        List<String> typeParams = typeParams();
        List<TypeRef> supers = supers();
        List<Member> members = memberBlock();
        return new ClassDecl(pos(t), decorators, modifiers, name, typeParams, supers, members);
    }

    private InterfaceDecl interfaceDecl(List<Decorator> decorators) {
        Token t = expect(INTERFACE, "'interface'");
        String name = expect(NAME, "un nom d'interface").text();
        List<String> typeParams = typeParams();
        List<TypeRef> supers = supers();
        List<Member> members = memberBlock();
        return new InterfaceDecl(pos(t), decorators, name, typeParams, supers, members);
    }

    private List<TypeRef> supers() {
        List<TypeRef> out = new ArrayList<>();
        if (match(LPAREN)) {
            if (!check(RPAREN)) {
                do {
                    out.add(type());
                } while (match(COMMA));
            }
            expect(RPAREN, "')'");
        }
        return out;
    }

    private List<Member> memberBlock() {
        expect(COLON, "':'");
        expect(NEWLINE, "une fin de ligne après ':'");
        expect(INDENT, "un bloc indenté");
        List<Member> members = new ArrayList<>();
        while (!check(DEDENT) && !check(EOF)) {
            Member m = member();
            if (m != null) {
                members.add(m);
            }
        }
        expect(DEDENT, "la fin du bloc");
        return members;
    }

    /** Retourne null pour un {@code pass}. */
    private Member member() {
        if (check(PASS)) {
            advance();
            endOfLine();
            return null;
        }
        if (check(AT) || check(DEF) || check(ABSTRACT) || check(FINAL)) {
            List<Decorator> decorators = decorators();
            List<String> modifiers = modifiers();
            return funcDecl(decorators, modifiers);
        }
        if (check(VAL) || check(VAR) || (check(NAME) && checkAt(1, COLON))) {
            return varDecl();
        }
        throw error(peek(), "membre attendu (def, champ ou 'pass') mais trouvé " + describe(peek()));
    }

    private EnumDecl enumDecl(List<Decorator> decorators) {
        Token t = expect(ENUM, "'enum'");
        String name = expect(NAME, "un nom d'énumération").text();
        expect(COLON, "':'");
        expect(NEWLINE, "une fin de ligne après ':'");
        expect(INDENT, "un bloc indenté");
        List<EnumConstant> constants = new ArrayList<>();
        while (check(NAME) && (checkAt(1, COMMA) || checkAt(1, NEWLINE))) {
            do {
                Token n = expect(NAME, "une constante");
                constants.add(new EnumConstant(pos(n), n.text()));
            } while (match(COMMA) && !check(NEWLINE));
            endOfLine();
        }
        List<Member> members = new ArrayList<>();
        while (!check(DEDENT) && !check(EOF)) {
            Member m = member();
            if (m != null) {
                members.add(m);
            }
        }
        expect(DEDENT, "la fin du bloc");
        if (constants.isEmpty()) {
            throw error(t, "une énumération doit déclarer au moins une constante");
        }
        return new EnumDecl(pos(t), decorators, name, constants, members);
    }

    // ------------------------------------------------------------------
    // Types
    // ------------------------------------------------------------------

    private TypeRef type() {
        Token start = peek();
        if (check(LPAREN)) {
            advance();
            List<TypeRef> params = new ArrayList<>();
            if (!check(RPAREN)) {
                do {
                    params.add(type());
                } while (match(COMMA));
            }
            expect(RPAREN, "')'");
            expect(ARROW, "'->' (type fonction)");
            TypeRef result = type();
            return new FuncType(pos(start), params, result, false);
        }
        String name = dottedName("un nom de type");
        List<TypeRef> args = new ArrayList<>();
        if (match(LBRACKET)) {
            do {
                args.add(type());
            } while (match(COMMA));
            expect(RBRACKET, "']'");
        }
        boolean nullable = match(QUESTION);
        return new NamedType(pos(start), name, args, nullable);
    }

    // ------------------------------------------------------------------
    // Instructions
    // ------------------------------------------------------------------

    /** {@code ':'} puis un bloc indenté, ou une instruction simple sur la même ligne. */
    private List<Stmt> suite() {
        expect(COLON, "':'");
        List<Stmt> body = new ArrayList<>();
        if (match(NEWLINE)) {
            expect(INDENT, "un bloc indenté");
            while (!check(DEDENT) && !check(EOF)) {
                body.add(statement());
            }
            expect(DEDENT, "la fin du bloc");
        } else {
            body.add(statement());
        }
        return body;
    }

    private Stmt statement() {
        Token t = peek();
        switch (t.type()) {
            case IF:
                return ifStmt();
            case WHILE: {
                advance();
                Expr cond = expr();
                return new WhileStmt(pos(t), cond, suite());
            }
            case FOR: {
                advance();
                List<String> vars = nameList();
                expect(IN, "'in'");
                Expr iterable = expr();
                return new ForStmt(pos(t), vars, iterable, suite());
            }
            case TRY:
                return tryStmt();
            case WITH: {
                advance();
                Expr ctx = expr();
                String name = match(AS) ? expect(NAME, "un nom").text() : null;
                return new WithStmt(pos(t), ctx, name, suite());
            }
            case MATCH:
                return matchStmt();
            case AT:
            case DEF:
                return funcDecl(decorators(), List.of());
            case VAL:
            case VAR:
                return varDecl();
            case RETURN: {
                advance();
                Expr value = check(NEWLINE) ? null : expr();
                endOfLine();
                return new ReturnStmt(pos(t), value);
            }
            case BREAK:
                advance();
                endOfLine();
                return new BreakStmt(pos(t));
            case CONTINUE:
                advance();
                endOfLine();
                return new ContinueStmt(pos(t));
            case PASS:
                advance();
                endOfLine();
                return new PassStmt(pos(t));
            case RAISE: {
                advance();
                Expr value = expr();
                endOfLine();
                return new RaiseStmt(pos(t), value);
            }
            case ASSERT: {
                advance();
                Expr cond = expr();
                Expr message = match(COMMA) ? expr() : null;
                endOfLine();
                return new AssertStmt(pos(t), cond, message);
            }
            default:
                if (check(NAME) && checkAt(1, COLON)) {
                    return varDecl();
                }
                return simpleStatement();
        }
    }

    private Stmt simpleStatement() {
        Expr e = expr();
        AssignOp op = assignOp(peek().type());
        if (op != null) {
            Token opToken = advance();
            if (!(e instanceof Name || e instanceof Attribute || e instanceof Index)) {
                throw error(opToken, "cible d'affectation invalide");
            }
            Expr value = expr();
            endOfLine();
            return new Assign(e.pos(), e, op, value);
        }
        endOfLine();
        return new ExprStmt(e.pos(), e);
    }

    private static AssignOp assignOp(TokenType t) {
        switch (t) {
            case ASSIGN: return AssignOp.ASSIGN;
            case PLUS_ASSIGN: return AssignOp.ADD;
            case MINUS_ASSIGN: return AssignOp.SUB;
            case STAR_ASSIGN: return AssignOp.MUL;
            case SLASH_ASSIGN: return AssignOp.DIV;
            case PERCENT_ASSIGN: return AssignOp.MOD;
            default: return null;
        }
    }

    private VarDecl varDecl() {
        Token t = peek();
        Mutability mut = Mutability.UNSPECIFIED;
        if (match(VAL)) {
            mut = Mutability.VAL;
        } else if (match(VAR)) {
            mut = Mutability.VAR;
        }
        String name = expect(NAME, "un nom de variable").text();
        TypeRef type = match(COLON) ? type() : null;
        Expr init = match(ASSIGN) ? expr() : null;
        if (type == null && init == null) {
            throw error(t, "'" + name + "' : un type ou une valeur initiale est requis");
        }
        endOfLine();
        return new VarDecl(pos(t), mut, name, type, init);
    }

    private IfStmt ifStmt() {
        Token t = advance(); // IF ou ELIF
        Expr cond = expr();
        List<Stmt> then = suite();
        List<Stmt> orElse = new ArrayList<>();
        if (check(ELIF)) {
            orElse.add(ifStmt());
        } else if (match(ELSE)) {
            orElse = suite();
        }
        return new IfStmt(pos(t), cond, then, orElse);
    }

    private TryStmt tryStmt() {
        Token t = advance();
        List<Stmt> body = suite();
        List<ExceptClause> handlers = new ArrayList<>();
        while (check(EXCEPT)) {
            Token e = advance();
            TypeRef type = null;
            String name = null;
            if (!check(COLON)) {
                type = type();
                if (match(AS)) {
                    name = expect(NAME, "un nom").text();
                }
            }
            handlers.add(new ExceptClause(pos(e), type, name, suite()));
        }
        List<Stmt> finallyBody = null;
        if (match(FINALLY)) {
            finallyBody = suite();
        }
        if (handlers.isEmpty() && finallyBody == null) {
            throw error(t, "'try' doit être suivi d'au moins un 'except' ou d'un 'finally'");
        }
        return new TryStmt(pos(t), body, handlers, finallyBody);
    }

    private MatchStmt matchStmt() {
        Token t = advance();
        Expr subject = expr();
        expect(COLON, "':'");
        expect(NEWLINE, "une fin de ligne après ':'");
        expect(INDENT, "un bloc indenté");
        List<MatchCase> cases = new ArrayList<>();
        while (check(CASE)) {
            Token c = advance();
            List<Expr> patterns = new ArrayList<>();
            patterns.add(orExpr());
            while (match(PIPE)) {
                patterns.add(orExpr());
            }
            cases.add(new MatchCase(pos(c), patterns, suite()));
        }
        if (cases.isEmpty()) {
            throw error(peek(), "'case' attendu dans un 'match' mais trouvé " + describe(peek()));
        }
        expect(DEDENT, "la fin du bloc");
        return new MatchStmt(pos(t), subject, cases);
    }

    private List<String> nameList() {
        List<String> out = new ArrayList<>();
        do {
            out.add(expect(NAME, "un nom de variable").text());
        } while (match(COMMA));
        return out;
    }

    // ------------------------------------------------------------------
    // Expressions
    // ------------------------------------------------------------------

    private Expr expr() {
        if (isLambdaStart()) {
            return lambda();
        }
        Expr e = orExpr();
        if (match(IF)) {
            Expr cond = orExpr();
            expect(ELSE, "'else' (expression conditionnelle)");
            Expr other = expr();
            return new Conditional(e.pos(), cond, e, other);
        }
        return e;
    }

    private boolean isLambdaStart() {
        if (check(NAME) && checkAt(1, ARROW)) {
            return true;
        }
        if (!check(LPAREN)) {
            return false;
        }
        int depth = 0;
        for (int i = p; i < tokens.size(); i++) {
            TokenType t = tokens.get(i).type();
            if (t == LPAREN || t == LBRACKET || t == LBRACE) {
                depth++;
            } else if (t == RPAREN || t == RBRACKET || t == RBRACE) {
                depth--;
                if (depth == 0) {
                    return i + 1 < tokens.size() && tokens.get(i + 1).type() == ARROW;
                }
            } else if (t == NEWLINE || t == EOF) {
                return false;
            }
        }
        return false;
    }

    private Expr lambda() {
        Token start = peek();
        List<Param> params = new ArrayList<>();
        if (check(NAME)) {
            Token n = advance();
            params.add(new Param(pos(n), n.text(), null, null));
        } else {
            expect(LPAREN, "'('");
            while (!check(RPAREN)) {
                params.add(param());
                if (!match(COMMA)) {
                    break;
                }
            }
            expect(RPAREN, "')'");
        }
        expect(ARROW, "'->'");
        return new Lambda(pos(start), params, expr());
    }

    private Expr orExpr() {
        Expr left = andExpr();
        while (match(OR)) {
            left = new Binary(left.pos(), BinaryOp.OR, left, andExpr());
        }
        return left;
    }

    private Expr andExpr() {
        Expr left = notExpr();
        while (match(AND)) {
            left = new Binary(left.pos(), BinaryOp.AND, left, notExpr());
        }
        return left;
    }

    private Expr notExpr() {
        if (check(NOT)) {
            Token t = advance();
            return new Unary(pos(t), UnaryOp.NOT, notExpr());
        }
        return comparison();
    }

    private Expr comparison() {
        Expr left = elvis();
        while (true) {
            BinaryOp op;
            switch (peek().type()) {
                case EQ: op = BinaryOp.EQ; advance(); break;
                case NE: op = BinaryOp.NE; advance(); break;
                case LT: op = BinaryOp.LT; advance(); break;
                case GT: op = BinaryOp.GT; advance(); break;
                case LE: op = BinaryOp.LE; advance(); break;
                case GE: op = BinaryOp.GE; advance(); break;
                case IN: op = BinaryOp.IN; advance(); break;
                case NOT:
                    if (!checkAt(1, IN)) {
                        return left;
                    }
                    advance();
                    advance();
                    op = BinaryOp.NOT_IN;
                    break;
                case IS:
                    advance();
                    op = match(NOT) ? BinaryOp.IS_NOT : BinaryOp.IS;
                    break;
                default:
                    return left;
            }
            left = new Binary(left.pos(), op, left, elvis());
        }
    }

    private Expr elvis() {
        Expr left = additive();
        while (match(ELVIS)) {
            left = new Binary(left.pos(), BinaryOp.ELVIS, left, additive());
        }
        return left;
    }

    private Expr additive() {
        Expr left = multiplicative();
        while (check(PLUS) || check(MINUS)) {
            BinaryOp op = advance().type() == PLUS ? BinaryOp.ADD : BinaryOp.SUB;
            left = new Binary(left.pos(), op, left, multiplicative());
        }
        return left;
    }

    private Expr multiplicative() {
        Expr left = unary();
        while (check(STAR) || check(SLASH) || check(PERCENT)) {
            TokenType t = advance().type();
            BinaryOp op = t == STAR ? BinaryOp.MUL : t == SLASH ? BinaryOp.DIV : BinaryOp.MOD;
            left = new Binary(left.pos(), op, left, unary());
        }
        return left;
    }

    private Expr unary() {
        if (check(MINUS) || check(PLUS)) {
            Token t = advance();
            return new Unary(pos(t), t.type() == MINUS ? UnaryOp.NEG : UnaryOp.POS, unary());
        }
        return power();
    }

    private Expr power() {
        Expr base = postfix();
        if (match(STARSTAR)) {
            return new Binary(base.pos(), BinaryOp.POW, base, unary());
        }
        return base;
    }

    private Expr postfix() {
        Expr e = atom();
        while (true) {
            Token t = peek();
            if (t.type() == DOT || t.type() == QDOT) {
                advance();
                Token n = peek();
                if (!isNameLike(n)) {
                    throw error(n, "nom attendu après '" + t.text() + "' mais trouvé " + describe(n));
                }
                advance();
                e = new Attribute(e.pos(), e, n.text(), t.type() == QDOT);
            } else if (t.type() == LPAREN) {
                advance();
                e = new Call(e.pos(), e, callArgs());
            } else if (t.type() == LBRACKET) {
                advance();
                e = new Index(e.pos(), e, subscripts());
            } else {
                return e;
            }
        }
    }

    /** Appelé après « ( » ; consomme jusqu'à « ) » inclus. */
    private List<Arg> callArgs() {
        List<Arg> args = new ArrayList<>();
        if (match(RPAREN)) {
            return args;
        }
        while (true) {
            Token t = peek();
            if (check(NAME) && checkAt(1, ASSIGN)) {
                String name = advance().text();
                advance();
                args.add(new Arg(pos(t), name, expr()));
            } else {
                Expr e = expr();
                if (check(FOR)) {
                    e = new GeneratorExp(pos(t), e, compClauses());
                }
                args.add(new Arg(pos(t), null, e));
            }
            if (match(COMMA)) {
                if (check(RPAREN)) {
                    break;
                }
                continue;
            }
            break;
        }
        expect(RPAREN, "')'");
        return args;
    }

    /** Appelé après « [ » ; consomme jusqu'à « ] » inclus. */
    private List<Expr> subscripts() {
        List<Expr> items = new ArrayList<>();
        Token start = peek();
        do {
            if (check(RBRACKET)) {
                break;
            }
            items.add(subscriptItem());
        } while (match(COMMA));
        if (items.isEmpty()) {
            throw error(start, "indice attendu entre '[' et ']'");
        }
        expect(RBRACKET, "']'");
        return items;
    }

    private Expr subscriptItem() {
        Token t = peek();
        Expr lower = null;
        if (!check(COLON)) {
            lower = expr();
            if (!check(COLON)) {
                return lower;
            }
        }
        expect(COLON, "':'");
        Expr upper = sliceBoundAbsent() ? null : expr();
        Expr step = null;
        if (match(COLON)) {
            step = sliceBoundAbsent() ? null : expr();
        }
        return new Slice(pos(t), lower, upper, step);
    }

    private boolean sliceBoundAbsent() {
        return check(COLON) || check(COMMA) || check(RBRACKET);
    }

    private List<CompClause> compClauses() {
        List<CompClause> out = new ArrayList<>();
        while (check(FOR)) {
            Token f = advance();
            List<String> vars = nameList();
            expect(IN, "'in'");
            Expr iterable = orExpr();
            List<Expr> conditions = new ArrayList<>();
            while (match(IF)) {
                conditions.add(orExpr());
            }
            out.add(new CompClause(pos(f), vars, iterable, conditions));
        }
        return out;
    }

    private Expr atom() {
        Token t = peek();
        switch (t.type()) {
            case INT:
                advance();
                return new IntLit(pos(t), t.text());
            case FLOAT:
                advance();
                return new FloatLit(pos(t), t.text());
            case STRING:
                advance();
                return new StringLit(pos(t), t.text());
            case FSTRING:
                advance();
                return fstring(t);
            case TRUE:
                advance();
                return new BoolLit(pos(t), true);
            case FALSE:
                advance();
                return new BoolLit(pos(t), false);
            case NULL:
                advance();
                return new NullLit(pos(t));
            case NAME:
                advance();
                return new Name(pos(t), t.text());
            case LPAREN: {
                advance();
                Expr e = expr();
                if (check(COMMA)) {
                    throw error(peek(), "les tuples ne sont pas encore supportés");
                }
                expect(RPAREN, "')'");
                return e;
            }
            case LBRACKET:
                return listDisplay();
            case LBRACE:
                return braceDisplay();
            default:
                throw error(t, "expression attendue mais trouvé " + describe(t));
        }
    }

    private Expr listDisplay() {
        Token open = advance();
        if (match(RBRACKET)) {
            return new ListLit(pos(open), new ArrayList<>());
        }
        Expr first = expr();
        if (check(FOR)) {
            List<CompClause> clauses = compClauses();
            expect(RBRACKET, "']'");
            return new ListComp(pos(open), first, clauses);
        }
        List<Expr> elements = new ArrayList<>();
        elements.add(first);
        while (match(COMMA)) {
            if (check(RBRACKET)) {
                break;
            }
            elements.add(expr());
        }
        expect(RBRACKET, "']'");
        return new ListLit(pos(open), elements);
    }

    private Expr braceDisplay() {
        Token open = advance();
        if (match(RBRACE)) {
            return new MapLit(pos(open), new ArrayList<>());
        }
        Expr first = expr();
        if (match(COLON)) {
            Expr value = expr();
            if (check(FOR)) {
                List<CompClause> clauses = compClauses();
                expect(RBRACE, "'}'");
                return new MapComp(pos(open), first, value, clauses);
            }
            List<MapEntry> entries = new ArrayList<>();
            entries.add(new MapEntry(first, value));
            while (match(COMMA)) {
                if (check(RBRACE)) {
                    break;
                }
                Expr k = expr();
                expect(COLON, "':'");
                entries.add(new MapEntry(k, expr()));
            }
            expect(RBRACE, "'}'");
            return new MapLit(pos(open), entries);
        }
        if (check(FOR)) {
            List<CompClause> clauses = compClauses();
            expect(RBRACE, "'}'");
            return new SetComp(pos(open), first, clauses);
        }
        List<Expr> elements = new ArrayList<>();
        elements.add(first);
        while (match(COMMA)) {
            if (check(RBRACE)) {
                break;
            }
            elements.add(expr());
        }
        expect(RBRACE, "'}'");
        return new SetLit(pos(open), elements);
    }

    // ------------------------------------------------------------------
    // f-strings
    // ------------------------------------------------------------------

    private Expr fstring(Token t) {
        Pos pos = pos(t);
        String raw = t.text();
        int n = raw.length();
        List<FStringPart> parts = new ArrayList<>();
        StringBuilder lit = new StringBuilder();
        int i = 0;
        while (i < n) {
            char c = raw.charAt(i);
            if (c == '{') {
                if (i + 1 < n && raw.charAt(i + 1) == '{') {
                    lit.append('{');
                    i += 2;
                    continue;
                }
                int end = findExprEnd(raw, i + 1, t);
                if (lit.length() > 0) {
                    parts.add(new FText(lit.toString()));
                    lit.setLength(0);
                }
                parts.add(fstringExpr(raw.substring(i + 1, end), t));
                i = end + 1;
            } else if (c == '}') {
                lit.append('}');
                i += (i + 1 < n && raw.charAt(i + 1) == '}') ? 2 : 1;
            } else if (c == '\\' && i + 1 < n) {
                i = fstringEscape(raw, i, lit, t);
            } else {
                lit.append(c);
                i++;
            }
        }
        if (lit.length() > 0) {
            parts.add(new FText(lit.toString()));
        }
        return new FStringLit(pos, parts);
    }

    private int fstringEscape(String raw, int i, StringBuilder out, Token t) {
        char e = raw.charAt(i + 1);
        switch (e) {
            case 'n': out.append('\n'); return i + 2;
            case 't': out.append('\t'); return i + 2;
            case 'r': out.append('\r'); return i + 2;
            case '0': out.append('\0'); return i + 2;
            case '\\': out.append('\\'); return i + 2;
            case '\'': out.append('\''); return i + 2;
            case '"': out.append('"'); return i + 2;
            case 'u':
                try {
                    out.append((char) Integer.parseInt(raw.substring(i + 2, i + 6), 16));
                    return i + 6;
                } catch (RuntimeException ex) {
                    throw error(t, "séquence \\u invalide dans la f-string");
                }
            default:
                throw error(t, "séquence d'échappement inconnue dans la f-string : \\" + e);
        }
    }

    /** Index du « } » qui ferme l'expression commencée en {@code from}. */
    private int findExprEnd(String raw, int from, Token t) {
        int depth = 1;
        int i = from;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '\'' || c == '"') {
                int j = i + 1;
                while (j < raw.length() && raw.charAt(j) != c) {
                    j++;
                }
                i = j + 1;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        throw error(t, "'{' non fermée dans la f-string");
    }

    private FExpr fstringExpr(String inner, Token t) {
        int split = formatSeparator(inner);
        String exprText = (split < 0 ? inner : inner.substring(0, split)).strip();
        String spec = split < 0 ? null : inner.substring(split + 1);
        if (exprText.isEmpty()) {
            throw error(t, "expression vide dans la f-string");
        }
        List<Token> raw;
        try {
            raw = Lexer.tokenize(exprText + "\n");
        } catch (LexerException ex) {
            throw error(t, "dans la f-string : " + ex.getMessage());
        }
        List<Token> mapped = new ArrayList<>();
        for (Token tk : raw) {
            mapped.add(new Token(tk.type(), tk.text(), t.line(), t.column()));
        }
        Parser sub = new Parser(mapped);
        Expr e = sub.expr();
        sub.expect(NEWLINE, "la fin de l'expression dans la f-string");
        sub.expect(EOF, "la fin de l'expression dans la f-string");
        return new FExpr(e, spec);
    }

    /** Index du « : » de format au niveau 0 (hors crochets, chaînes et « ?: »), ou -1. */
    private static int formatSeparator(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' || c == '"') {
                int j = i + 1;
                while (j < s.length() && s.charAt(j) != c) {
                    j++;
                }
                i = j;
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ':' && depth == 0 && !(i > 0 && s.charAt(i - 1) == '?')) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private Token peek() {
        return tokens.get(p);
    }

    private boolean check(TokenType t) {
        return peek().type() == t;
    }

    private boolean checkAt(int offset, TokenType t) {
        int i = Math.min(p + offset, tokens.size() - 1);
        return tokens.get(i).type() == t;
    }

    private Token advance() {
        Token t = tokens.get(p);
        if (t.type() != EOF) {
            p++;
        }
        return t;
    }

    private boolean match(TokenType t) {
        if (check(t)) {
            advance();
            return true;
        }
        return false;
    }

    private Token expect(TokenType t, String what) {
        if (check(t)) {
            return advance();
        }
        throw error(peek(), "attendu " + what + " mais trouvé " + describe(peek()));
    }

    private void endOfLine() {
        expect(NEWLINE, "une fin de ligne");
    }

    /** Vrai pour un nom ou un mot-clé (autorisé après un « . », ex. {@code pattern.match(...)}). */
    private static boolean isNameLike(Token t) {
        return t.type() == NAME
                || (t.type().ordinal() >= DEF.ordinal() && t.type().ordinal() <= IS.ordinal());
    }

    private static Pos pos(Token t) {
        return new Pos(t.line(), t.column());
    }

    private static ParseException error(Token t, String message) {
        return new ParseException(message, t.line(), t.column());
    }

    private static String describe(Token t) {
        switch (t.type()) {
            case NEWLINE: return "une fin de ligne";
            case INDENT: return "une indentation inattendue";
            case DEDENT: return "une fin de bloc";
            case EOF: return "la fin du fichier";
            case STRING:
            case FSTRING: return "une chaîne";
            default: return "'" + t.text() + "'";
        }
    }
}
