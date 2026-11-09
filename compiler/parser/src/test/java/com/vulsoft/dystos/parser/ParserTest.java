package com.vulsoft.dystos.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vulsoft.dystos.ast.Ast.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ParserTest {

    private static Expr e(String src) {
        return Parser.parseExpression(src);
    }

    private static SourceFile file(String src) {
        return Parser.parse(src);
    }

    private static FuncDecl main(String body) {
        StringBuilder sb = new StringBuilder("def main():\n");
        for (String line : body.split("\n")) {
            sb.append("    ").append(line).append("\n");
        }
        return (FuncDecl) file(sb.toString()).decls().get(0);
    }

    // ---------------- Expressions ----------------

    @Test
    void multiplicationBindsTighterThanAddition() {
        Binary b = (Binary) e("1 + 2 * 3");
        assertEquals(BinaryOp.ADD, b.op());
        assertEquals(BinaryOp.MUL, ((Binary) b.right()).op());
    }

    @Test
    void powerIsRightAssociativeAndBindsTighterThanUnaryMinus() {
        Binary b = (Binary) e("2 ** 3 ** 2");
        assertEquals(BinaryOp.POW, ((Binary) b.right()).op());
        Unary u = (Unary) e("-2 ** 2");
        assertEquals(UnaryOp.NEG, u.op());
        assertEquals(BinaryOp.POW, ((Binary) u.operand()).op());
        assertEquals(UnaryOp.NEG, ((Unary) ((Binary) e("2 ** -1")).right()).op());
    }

    @Test
    void logicalOperatorsAndComparisons() {
        Binary b = (Binary) e("a or b and not c == d");
        assertEquals(BinaryOp.OR, b.op());
        Binary and = (Binary) b.right();
        assertEquals(BinaryOp.AND, and.op());
        Unary not = (Unary) and.right();
        assertEquals(BinaryOp.EQ, ((Binary) not.operand()).op());
    }

    @Test
    void membershipAndIdentityOperators() {
        assertEquals(BinaryOp.NOT_IN, ((Binary) e("x not in xs")).op());
        assertEquals(BinaryOp.IS_NOT, ((Binary) e("x is not null")).op());
        assertEquals(BinaryOp.IN, ((Binary) e("x in xs")).op());
    }

    @Test
    void elvisBindsTighterThanComparisonLooserThanAddition() {
        Binary b = (Binary) e("a ?: 0 + 1");
        assertEquals(BinaryOp.ELVIS, b.op());
        assertEquals(BinaryOp.ADD, ((Binary) b.right()).op());
        assertEquals(BinaryOp.EQ, ((Binary) e("a ?: 0 == 1")).op());
    }

    @Test
    void safeAccessAndCalls() {
        Call c = (Call) e("p?.nom.upper(1, k=2)");
        assertEquals(2, c.args().size());
        assertEquals("k", c.args().get(1).name());
        Attribute upper = (Attribute) c.callee();
        assertEquals("upper", upper.name());
        Attribute nom = (Attribute) upper.target();
        assertTrue(nom.safe());
    }

    @Test
    void keywordsAreAllowedAfterDot() {
        Attribute a = (Attribute) e("pattern.match");
        assertEquals("match", a.name());
    }

    @Test
    void conditionalExpression() {
        Conditional c = (Conditional) e("1 if ok else 2");
        assertTrue(c.cond() instanceof Name);
        assertTrue(c.thenValue() instanceof IntLit);
    }

    @Test
    void lambdas() {
        Lambda one = (Lambda) e("x -> x + 1");
        assertEquals(1, one.params().size());
        Lambda two = (Lambda) e("(a, b) -> a + b");
        assertEquals(2, two.params().size());
        Lambda none = (Lambda) e("() -> 42");
        assertEquals(0, none.params().size());
        Lambda typed = (Lambda) e("(a: int) -> a");
        assertEquals("int", ((NamedType) typed.params().get(0).type()).name());
    }

    @Test
    void parenthesizedExpressionIsNotALambda() {
        assertTrue(e("(1 + 2) * 3") instanceof Binary);
    }

    @Test
    void collectionLiterals() {
        assertEquals(3, ((ListLit) e("[1, 2, 3]")).elements().size());
        assertEquals(2, ((SetLit) e("{1, 2}")).elements().size());
        MapLit m = (MapLit) e("{\"a\": 1, \"b\": 2,}");
        assertEquals(2, m.entries().size());
        assertTrue(((MapLit) e("{}")).entries().isEmpty());
    }

    @Test
    void comprehensions() {
        ListComp lc = (ListComp) e("[x * x for x in range(10) if x % 2 == 0]");
        assertEquals(1, lc.clauses().size());
        assertEquals(1, lc.clauses().get(0).conditions().size());
        MapComp mc = (MapComp) e("{m: len(m) for m in ms}");
        assertEquals(1, mc.clauses().size());
        SetComp sc = (SetComp) e("{x for x in xs if x}");
        assertEquals(1, sc.clauses().get(0).conditions().size());
        ListComp two = (ListComp) e("[a + b for a in xs for b in ys]");
        assertEquals(2, two.clauses().size());
    }

    @Test
    void generatorExpressionAsArgument() {
        Call c = (Call) e("sum(x * 2 for x in xs)");
        assertTrue(c.args().get(0).value() instanceof GeneratorExp);
    }

    @Test
    void indexingSlicesAndGenericInstantiation() {
        Index i = (Index) e("m[str, int]");
        assertEquals(2, i.indices().size());
        Index s = (Index) e("s[0:4]");
        Slice slice = (Slice) s.indices().get(0);
        assertTrue(slice.lower() instanceof IntLit);
        assertTrue(slice.upper() instanceof IntLit);
        Slice open = (Slice) ((Index) e("s[:3]")).indices().get(0);
        assertEquals(null, open.lower());
        Call c = (Call) e("Boite[int](42)");
        assertTrue(c.callee() instanceof Index);
    }

    @Test
    void literals() {
        assertEquals("1000", ((IntLit) e("1_000")).text());
        assertEquals("3.5", ((FloatLit) e("3.5")).text());
        assertEquals("a\nb", ((StringLit) e("\"a\\nb\"")).value());
        assertEquals(true, ((BoolLit) e("true")).value());
        assertTrue(e("null") instanceof NullLit);
    }

    // ---------------- f-strings ----------------

    @Test
    void fstringParts() {
        FStringLit f = (FStringLit) e("f\"Aire : {x.aire():.2f} m{{2}}\"");
        assertEquals(3, f.parts().size());
        assertEquals("Aire : ", ((FText) f.parts().get(0)).text());
        FExpr fe = (FExpr) f.parts().get(1);
        assertTrue(fe.expr() instanceof Call);
        assertEquals(".2f", fe.spec());
        assertEquals(" m{2}", ((FText) f.parts().get(2)).text());
    }

    @Test
    void fstringWithNestedQuotesAndTernary() {
        FStringLit f = (FStringLit) e("f\"{'ok' if p > 3 else 'ko'}\"");
        FExpr fe = (FExpr) f.parts().get(0);
        assertTrue(fe.expr() instanceof Conditional);
        assertEquals(null, fe.spec());
    }

    @Test
    void fstringElvisIsNotAFormatSpec() {
        FStringLit f = (FStringLit) e("f\"{a ?: 0}\"");
        FExpr fe = (FExpr) f.parts().get(0);
        assertEquals(BinaryOp.ELVIS, ((Binary) fe.expr()).op());
        assertEquals(null, fe.spec());
    }

    @Test
    void fstringEmptyExpressionIsAnError() {
        assertThrows(ParseException.class, () -> e("f\"{}\""));
    }

    // ---------------- Instructions ----------------

    @Test
    void variableDeclarations() {
        List<Stmt> b = main("val a = 1\nvar b = 2\nc: float = 1.5\nd = 4").body();
        VarDecl a = (VarDecl) b.get(0);
        assertEquals(Mutability.VAL, a.mutability());
        assertEquals(Mutability.VAR, ((VarDecl) b.get(1)).mutability());
        VarDecl c = (VarDecl) b.get(2);
        assertEquals(Mutability.UNSPECIFIED, c.mutability());
        assertEquals("float", ((NamedType) c.type()).name());
        assertTrue(b.get(3) instanceof Assign);
    }

    @Test
    void valWithoutTypeOrInitIsAnError() {
        assertThrows(ParseException.class, () -> main("val a"));
    }

    @Test
    void augmentedAssignmentToAttributeAndIndex() {
        List<Stmt> b = main("self.x += 1\nm[\"k\"] = 2").body();
        Assign a = (Assign) b.get(0);
        assertEquals(AssignOp.ADD, a.op());
        assertTrue(a.target() instanceof Attribute);
        assertTrue(((Assign) b.get(1)).target() instanceof Index);
    }

    @Test
    void invalidAssignmentTarget() {
        assertThrows(ParseException.class, () -> main("1 = 2"));
        assertThrows(ParseException.class, () -> main("f() = 2"));
    }

    @Test
    void ifElifElseIsNested() {
        IfStmt i = (IfStmt) main("if a:\n    pass\nelif b:\n    pass\nelse:\n    pass").body().get(0);
        assertEquals(1, i.orElse().size());
        IfStmt elif = (IfStmt) i.orElse().get(0);
        assertEquals(1, elif.orElse().size());
        assertTrue(elif.orElse().get(0) instanceof PassStmt);
    }

    @Test
    void loops() {
        List<Stmt> b = main("for k, v in m.items():\n    continue\nwhile true:\n    break").body();
        ForStmt f = (ForStmt) b.get(0);
        assertEquals(List.of("k", "v"), f.vars());
        assertTrue(b.get(1) instanceof WhileStmt);
    }

    @Test
    void singleLineSuite() {
        IfStmt i = (IfStmt) main("if a: return 1").body().get(0);
        assertTrue(i.then().get(0) instanceof ReturnStmt);
    }

    @Test
    void tryExceptFinally() {
        TryStmt t = (TryStmt) main(
                "try:\n    f()\nexcept Erreur as e:\n    g()\nexcept:\n    h()\nfinally:\n    k()").body().get(0);
        assertEquals(2, t.handlers().size());
        assertEquals("e", t.handlers().get(0).name());
        assertEquals(null, t.handlers().get(1).type());
        assertEquals(1, t.finallyBody().size());
    }

    @Test
    void tryAloneIsAnError() {
        assertThrows(ParseException.class, () -> main("try:\n    f()"));
    }

    @Test
    void withAndRaiseAndAssert() {
        List<Stmt> b = main("with io.open(\"a\") as f:\n    pass\nraise E(1)\nassert x == 1, \"msg\"").body();
        assertEquals("f", ((WithStmt) b.get(0)).name());
        assertTrue(b.get(1) instanceof RaiseStmt);
        assertTrue(((AssertStmt) b.get(2)).message() != null);
    }

    @Test
    void matchWithAlternativesAndWildcard() {
        MatchStmt m = (MatchStmt) main(
                "match j:\n    case A.X | A.Y:\n        pass\n    case _:\n        pass").body().get(0);
        assertEquals(2, m.cases().size());
        assertEquals(2, m.cases().get(0).patterns().size());
        assertEquals("_", ((Name) m.cases().get(1).patterns().get(0)).name());
    }

    // ---------------- Déclarations ----------------

    @Test
    void packageImportsAndTopLevel() {
        SourceFile f = file("package app.demo\n\nimport java.util.List\nimport java.time.*\nimport io as e\n\ndef main():\n    pass\n");
        assertEquals("app.demo", f.packageName());
        assertEquals(3, f.imports().size());
        assertEquals("java.time.*", f.imports().get(1).path());
        assertEquals("e", f.imports().get(2).alias());
        assertEquals(1, f.decls().size());
    }

    @Test
    void functionSignature() {
        FuncDecl fn = (FuncDecl) file("def f[T](a: list[T], b: int = 3) -> T?:\n    pass\n").decls().get(0);
        assertEquals(List.of("T"), fn.typeParams());
        assertEquals(2, fn.params().size());
        assertTrue(fn.params().get(1).defaultValue() instanceof IntLit);
        NamedType ret = (NamedType) fn.returnType();
        assertTrue(ret.nullable());
    }

    @Test
    void functionTypes() {
        FuncDecl fn = (FuncDecl) file("def ap(f: (int, int) -> int) -> (int) -> int:\n    pass\n").decls().get(0);
        FuncType ft = (FuncType) fn.params().get(0).type();
        assertEquals(2, ft.params().size());
        assertTrue(fn.returnType() instanceof FuncType);
    }

    @Test
    void classWithMembers() {
        ClassDecl c = (ClassDecl) file(
                "abstract class A[T](B, C):\n    x: int\n    var y = 2\n    def init(self):\n        pass\n    abstract def f(self)\n").decls().get(0);
        assertEquals(List.of("abstract"), c.modifiers());
        assertEquals(List.of("T"), c.typeParams());
        assertEquals(2, c.supers().size());
        assertEquals(4, c.members().size());
        FuncDecl f = (FuncDecl) c.members().get(3);
        assertEquals(null, f.body());
        assertEquals(List.of("abstract"), f.modifiers());
    }

    @Test
    void interfaceMethodsHaveNoBody() {
        InterfaceDecl i = (InterfaceDecl) file("interface Forme:\n    def aire(self) -> float\n").decls().get(0);
        assertEquals(null, ((FuncDecl) i.members().get(0)).body());
    }

    @Test
    void enumConstants() {
        EnumDecl en = (EnumDecl) file("enum Jour:\n    LUNDI, MARDI,\n    SAMEDI\n").decls().get(0);
        assertEquals(3, en.constants().size());
    }

    @Test
    void decorators() {
        FuncDecl fn = (FuncDecl) file("@test\n@retry(3)\ndef t():\n    pass\n").decls().get(0);
        assertEquals(2, fn.decorators().size());
        assertEquals("retry", fn.decorators().get(1).name());
        assertEquals(1, fn.decorators().get(1).args().size());
    }

    // ---------------- Erreurs ----------------

    @Test
    void missingColonReportsPosition() {
        ParseException ex = assertThrows(ParseException.class, () -> file("def main()\n    pass\n"));
        // « def main() » sans corps est une méthode sans corps ; l'erreur vient de l'indentation
        assertEquals(2, ex.line());
    }

    @Test
    void ifWithoutColon() {
        ParseException ex = assertThrows(ParseException.class, () -> main("if a\n    pass"));
        assertEquals(2, ex.line());
    }

    @Test
    void fileLevelStatementsMakeAScript() {
        SourceFile f = file("val x = 1\nprint(x)\n");
        assertTrue(f.isScript());
        assertEquals(2, f.statements().size());
        assertTrue(f.decls().isEmpty());
        assertTrue(f.statements().get(0) instanceof VarDecl);
    }

    @Test
    void declarationsAreHoistedAndStatementsKeepTheirOrder() {
        SourceFile f = file("a = 1\ndef f():\n    pass\nf()\nclass C:\n    pass\nb = 2\n");
        assertEquals(2, f.decls().size());
        assertEquals(3, f.statements().size());
        assertEquals("a", ((Name) ((Assign) f.statements().get(0)).target()).name());
        assertEquals("b", ((Name) ((Assign) f.statements().get(2)).target()).name());
    }

    @Test
    void fileWithOnlyDeclarationsIsNotAScript() {
        assertTrue(!file("def main():\n    pass\n").isScript());
    }

    @Test
    void fileLevelControlFlowIsAccepted() {
        SourceFile f = file("for i in range(3):\n    print(i)\n");
        assertTrue(f.statements().get(0) instanceof ForStmt);
    }

    @Test
    void strayIndentationAtFileStartIsAnError() {
        assertThrows(ParseException.class, () -> file("  x = 1\n"));
    }

    @Test
    void unexpectedTokenInExpression() {
        assertThrows(ParseException.class, () -> e("1 + * 2"));
        assertThrows(ParseException.class, () -> e("(1, 2)"));
    }

    // ---------------- Les 20 exemples ----------------

    @Test
    void allPhase0ExamplesParse() throws IOException {
        Path dir = Path.of("src/test/resources/exemples");
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.toString().endsWith(".dys")).sorted().collect(Collectors.toList());
        }
        assertEquals(21, files.size());
        for (Path p : files) {
            try {
                SourceFile f = Parser.parse(Files.readString(p));
                assertTrue(!f.decls().isEmpty(), p.getFileName().toString());
                assertTrue(!AstPrinter.print(f).isEmpty());
            } catch (RuntimeException ex) {
                throw new AssertionError(p.getFileName() + " : " + ex.getMessage(), ex);
            }
        }
    }

    @Test
    void stockExampleHasExpectedShape() throws IOException {
        SourceFile f = Parser.parse(Files.readString(
                Path.of("src/test/resources/exemples/20-programme-complet-gestion-de-stock.dys")));
        assertEquals(3, f.decls().size());
        ClassDecl stock = (ClassDecl) f.decls().get(1);
        assertEquals("Stock", stock.name());
        assertEquals(4, stock.members().size());
        FuncDecl trouver = (FuncDecl) stock.members().get(2);
        assertTrue(((NamedType) trouver.returnType()).nullable());
    }
}
