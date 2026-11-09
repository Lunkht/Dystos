package com.vulsoft.dystos.ast;

import java.util.List;

/**
 * Arbre syntaxique abstrait (AST) de Dystos.
 *
 * <p>Tous les nœuds sont des {@code record} immuables. Les interfaces scellées
 * ({@link Expr}, {@link Stmt}, {@link TypeRef}, {@link TopLevel}, {@link Member})
 * permettent d'utiliser le filtrage par motifs ({@code switch}) dans les phases suivantes.
 */
public final class Ast {

    private Ast() {
    }

    /** Position dans le source (à partir de 1). */
    public record Pos(int line, int column) {
        @Override
        public String toString() {
            return line + ":" + column;
        }
    }

    public interface Node {
        Pos pos();
    }

    // ------------------------------------------------------------------
    // Énumérations
    // ------------------------------------------------------------------

    /** Mutabilité déclarée : {@code val}, {@code var}, ou rien ({@code x = 1} / {@code x: int}). */
    public enum Mutability { VAL, VAR, UNSPECIFIED }

    public enum AssignOp { ASSIGN, ADD, SUB, MUL, DIV, MOD }

    public enum BinaryOp {
        ADD, SUB, MUL, DIV, MOD, POW,
        EQ, NE, LT, GT, LE, GE,
        AND, OR,
        IN, NOT_IN, IS, IS_NOT,
        ELVIS
    }

    public enum UnaryOp { NEG, POS, NOT }

    // ------------------------------------------------------------------
    // Fichier et déclarations de premier niveau
    // ------------------------------------------------------------------

    /**
     * Un fichier source.
     *
     * <p>Les déclarations (fonctions, classes, interfaces, enums) sont « hissées » dans {@code decls} ;
     * les instructions écrites directement au niveau du fichier sont dans {@code statements}, dans
     * leur ordre d'apparition. Un fichier avec des instructions est un <em>script</em> : l'analyse
     * sémantique les enveloppera dans un {@code main} implicite, et refusera un fichier qui
     * contient à la fois des instructions de niveau fichier et un {@code main} explicite.
     *
     * @param packageName nom du package ou {@code null}
     */
    public record SourceFile(Pos pos, String packageName, List<ImportDecl> imports, List<TopLevel> decls,
                             List<Stmt> statements) implements Node {

        public boolean isScript() {
            return !statements.isEmpty();
        }
    }

    /** @param path chemin pointé, éventuellement terminé par {@code .*} ; @param alias ou {@code null} */
    public record ImportDecl(Pos pos, String path, String alias) implements Node {
    }

    public sealed interface TopLevel extends Node {
    }

    public sealed interface Member extends Node {
    }

    public sealed interface Stmt extends Node {
    }

    /** Fonction ou méthode. {@code body == null} pour une méthode sans corps (interface, abstraite). */
    public record FuncDecl(Pos pos, List<Decorator> decorators, List<String> modifiers, String name,
                           List<String> typeParams, List<Param> params, TypeRef returnType,
                           List<Stmt> body) implements TopLevel, Member, Stmt {
    }

    public record ClassDecl(Pos pos, List<Decorator> decorators, List<String> modifiers, String name,
                            List<String> typeParams, List<TypeRef> supers, List<Member> members)
            implements TopLevel {
    }

    public record InterfaceDecl(Pos pos, List<Decorator> decorators, String name, List<String> typeParams,
                                List<TypeRef> supers, List<Member> members) implements TopLevel {
    }

    public record EnumDecl(Pos pos, List<Decorator> decorators, String name, List<EnumConstant> constants,
                           List<Member> members) implements TopLevel {
    }

    public record EnumConstant(Pos pos, String name) implements Node {
    }

    public record Decorator(Pos pos, String name, List<Arg> args) implements Node {
    }

    public record Param(Pos pos, String name, TypeRef type, Expr defaultValue) implements Node {
    }

    /** Variable locale ou champ : {@code val x = 1}, {@code var y: int}, {@code z: str = "a"}. */
    public record VarDecl(Pos pos, Mutability mutability, String name, TypeRef type, Expr init)
            implements Stmt, Member {
    }

    // ------------------------------------------------------------------
    // Types
    // ------------------------------------------------------------------

    public sealed interface TypeRef extends Node {
        boolean nullable();
    }

    /** @param name nom éventuellement pointé (ex. {@code java.util.List}) */
    public record NamedType(Pos pos, String name, List<TypeRef> args, boolean nullable) implements TypeRef {
    }

    /** Type fonction : {@code (int, int) -> int}. */
    public record FuncType(Pos pos, List<TypeRef> params, TypeRef result, boolean nullable) implements TypeRef {
    }

    // ------------------------------------------------------------------
    // Instructions
    // ------------------------------------------------------------------

    public record ExprStmt(Pos pos, Expr expr) implements Stmt {
    }

    public record Assign(Pos pos, Expr target, AssignOp op, Expr value) implements Stmt {
    }

    /** {@code elif} est représenté par un {@code IfStmt} imbriqué dans {@code orElse}. */
    public record IfStmt(Pos pos, Expr cond, List<Stmt> then, List<Stmt> orElse) implements Stmt {
    }

    public record WhileStmt(Pos pos, Expr cond, List<Stmt> body) implements Stmt {
    }

    public record ForStmt(Pos pos, List<String> vars, Expr iterable, List<Stmt> body) implements Stmt {
    }

    public record ReturnStmt(Pos pos, Expr value) implements Stmt {
    }

    public record BreakStmt(Pos pos) implements Stmt {
    }

    public record ContinueStmt(Pos pos) implements Stmt {
    }

    public record PassStmt(Pos pos) implements Stmt {
    }

    public record RaiseStmt(Pos pos, Expr value) implements Stmt {
    }

    public record AssertStmt(Pos pos, Expr cond, Expr message) implements Stmt {
    }

    public record TryStmt(Pos pos, List<Stmt> body, List<ExceptClause> handlers, List<Stmt> finallyBody)
            implements Stmt {
    }

    /** @param type type attrapé ou {@code null} (tout attraper) ; @param name variable ou {@code null} */
    public record ExceptClause(Pos pos, TypeRef type, String name, List<Stmt> body) implements Node {
    }

    /** @param name variable liée ({@code as f}) ou {@code null} */
    public record WithStmt(Pos pos, Expr context, String name, List<Stmt> body) implements Stmt {
    }

    public record MatchStmt(Pos pos, Expr subject, List<MatchCase> cases) implements Stmt {
    }

    /** @param patterns alternatives séparées par {@code |} ; le nom {@code _} est le joker */
    public record MatchCase(Pos pos, List<Expr> patterns, List<Stmt> body) implements Node {
    }

    // ------------------------------------------------------------------
    // Expressions
    // ------------------------------------------------------------------

    public sealed interface Expr extends Node {
    }

    /** @param text texte source sans les « _ » (préfixes 0x / 0b conservés) */
    public record IntLit(Pos pos, String text) implements Expr {
    }

    public record FloatLit(Pos pos, String text) implements Expr {
    }

    public record StringLit(Pos pos, String value) implements Expr {
    }

    public record FStringLit(Pos pos, List<FStringPart> parts) implements Expr {
    }

    public sealed interface FStringPart {
    }

    public record FText(String text) implements FStringPart {
    }

    /** @param spec format après le « : » (ex. {@code .2f}) ou {@code null} */
    public record FExpr(Expr expr, String spec) implements FStringPart {
    }

    public record BoolLit(Pos pos, boolean value) implements Expr {
    }

    public record NullLit(Pos pos) implements Expr {
    }

    public record Name(Pos pos, String name) implements Expr {
    }

    public record Binary(Pos pos, BinaryOp op, Expr left, Expr right) implements Expr {
    }

    public record Unary(Pos pos, UnaryOp op, Expr operand) implements Expr {
    }

    /** {@code thenValue if cond else elseValue} */
    public record Conditional(Pos pos, Expr cond, Expr thenValue, Expr elseValue) implements Expr {
    }

    public record Call(Pos pos, Expr callee, List<Arg> args) implements Expr {
    }

    /** @param name nom de l'argument nommé ou {@code null} */
    public record Arg(Pos pos, String name, Expr value) implements Node {
    }

    /** @param safe vrai pour {@code ?.} */
    public record Attribute(Pos pos, Expr target, String name, boolean safe) implements Expr {
    }

    /** {@code a[i]}, {@code m[k]}, {@code Boite[int]} ; plusieurs indices : {@code map[str, int]}. */
    public record Index(Pos pos, Expr target, List<Expr> indices) implements Expr {
    }

    /** Tranche {@code a:b:c} dans un indice ; les bornes absentes valent {@code null}. */
    public record Slice(Pos pos, Expr lower, Expr upper, Expr step) implements Expr {
    }

    public record ListLit(Pos pos, List<Expr> elements) implements Expr {
    }

    public record SetLit(Pos pos, List<Expr> elements) implements Expr {
    }

    /** {@code {}} vide est une map. */
    public record MapLit(Pos pos, List<MapEntry> entries) implements Expr {
    }

    public record MapEntry(Expr key, Expr value) {
    }

    public record Lambda(Pos pos, List<Param> params, Expr body) implements Expr {
    }

    public record ListComp(Pos pos, Expr element, List<CompClause> clauses) implements Expr {
    }

    public record SetComp(Pos pos, Expr element, List<CompClause> clauses) implements Expr {
    }

    public record MapComp(Pos pos, Expr key, Expr value, List<CompClause> clauses) implements Expr {
    }

    /** Expression génératrice passée en argument : {@code sum(x for x in xs)}. */
    public record GeneratorExp(Pos pos, Expr element, List<CompClause> clauses) implements Expr {
    }

    /** {@code for vars in iterable if cond if cond ...} */
    public record CompClause(Pos pos, List<String> vars, Expr iterable, List<Expr> conditions) implements Node {
    }
}
