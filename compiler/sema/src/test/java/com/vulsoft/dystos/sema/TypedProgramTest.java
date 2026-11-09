package com.vulsoft.dystos.sema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vulsoft.dystos.ast.Ast.*;
import com.vulsoft.dystos.sema.Symbols.VarKind;
import com.vulsoft.dystos.sema.TypedProgram.CallInfo;
import com.vulsoft.dystos.sema.TypedProgram.CallKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class TypedProgramTest {

    private static SemanticAnalyzer.Result analyze(String... lines) {
        SemanticAnalyzer.Result r = SemanticAnalyzer.check(String.join("\n", lines) + "\n");
        assertTrue(!r.hasErrors(), r.diagnostics().toString());
        assertNotNull(r.typed());
        return r;
    }

    private static FuncDecl func(TypedProgram t, String name) {
        for (TopLevel d : t.units.get(0).file().decls()) {
            if (d instanceof FuncDecl f && f.name().equals(name)) {
                return f;
            }
        }
        throw new AssertionError("fonction introuvable : " + name);
    }

    private static Expr assignedValue(FuncDecl f, int stmt) {
        return ((Assign) f.body().get(stmt)).value();
    }

    @Test
    void expressionTypesAreRecorded() {
        TypedProgram t = analyze("def main():", "    x = 1 + 2", "    y = 3 / 2", "    s = \"a\" + \"b\"", "    z = [1, 2]").typed();
        FuncDecl m = func(t, "main");
        assertEquals("int", t.typeOf(assignedValue(m, 0)).toString());
        assertEquals("float", t.typeOf(assignedValue(m, 1)).toString());
        assertEquals("str", t.typeOf(assignedValue(m, 2)).toString());
        assertEquals("list[int]", t.typeOf(assignedValue(m, 3)).toString());
    }

    @Test
    void declaredVariablesAreRecordedWithTheirInferredTypes() {
        TypedProgram t = analyze("def main():", "    x = 1", "    y: float = 2", "    val z = \"a\"", "    x = 5").typed();
        FuncDecl m = func(t, "main");
        assertEquals("int", t.declared.get(m.body().get(0)).get(0).type.toString());
        assertEquals("float", t.declared.get(m.body().get(1)).get(0).type.toString());
        assertEquals(false, t.declared.get(m.body().get(2)).get(0).mutable);
        assertEquals(null, t.declared.get(m.body().get(3)), "une réaffectation ne déclare rien");
    }

    @Test
    void callsRecordTheChosenTarget() {
        TypedProgram t = analyze(
                "class A:", "    x: int", "    def init(self, x: int):", "        self.x = x",
                "    def get(self) -> int:", "        return self.x",
                "class B:", "    pass",
                "def f(a: int) -> int:", "    return a",
                "def main():", "    a = A(1)", "    b = B()", "    n = a.get()", "    m = f(2)", "    k = int(\"3\")",
                "    print(m)").typed();
        FuncDecl main = func(t, "main");
        CallInfo ctor = t.calls.get((Call) assignedValue(main, 0));
        assertEquals(CallKind.CONSTRUCT, ctor.kind());
        assertEquals("init", ctor.func().name);
        assertEquals("A", ctor.cls().name);
        CallInfo dflt = t.calls.get((Call) assignedValue(main, 1));
        assertEquals(CallKind.CONSTRUCT, dflt.kind());
        assertEquals(null, dflt.func());
        assertEquals("B", dflt.cls().name);
        CallInfo method = t.calls.get((Call) assignedValue(main, 2));
        assertEquals(CallKind.METHOD, method.kind());
        assertEquals("get", method.func().name);
        CallInfo fn = t.calls.get((Call) assignedValue(main, 3));
        assertEquals(CallKind.FUNCTION, fn.kind());
        assertEquals("f", fn.func().name);
        assertEquals(CallKind.CONVERSION, t.calls.get((Call) assignedValue(main, 4)).kind());
        Call print = (Call) ((ExprStmt) main.body().get(5)).expr();
        assertEquals("print", t.calls.get(print).func().name);
    }

    @Test
    void genericCallsHaveTheirSubstitutedResultType() {
        TypedProgram t = analyze("def first[T](xs: list[T]) -> T:", "    return xs[0]",
                "def main():", "    a = first([1, 2])", "    b = first([\"x\"])").typed();
        FuncDecl m = func(t, "main");
        assertEquals("int", t.typeOf(assignedValue(m, 0)).toString());
        assertEquals("str", t.typeOf(assignedValue(m, 1)).toString());
    }

    @Test
    void membersAndEnumConstantsAreRecorded() {
        TypedProgram t = analyze("enum Couleur:", "    ROUGE, VERT",
                "class P:", "    n: int = 1", "    def get(self) -> int:", "        self.n = 2", "        return self.n",
                "def main():", "    c = Couleur.ROUGE", "    p = P()", "    k = p.n", "    o = Couleur.VERT.ordinal").typed();
        FuncDecl m = func(t, "main");
        Attribute enumConst = (Attribute) assignedValue(m, 0);
        assertEquals(VarKind.ENUM_CONSTANT, t.members.get(enumConst).field().kind);
        Attribute field = (Attribute) assignedValue(m, 2);
        assertEquals("n", t.members.get(field).field().name);
        Attribute prop = (Attribute) assignedValue(m, 3);
        assertEquals(VarKind.PROPERTY, t.members.get(prop).field().kind);
        ClassDecl p = (ClassDecl) t.units.get(0).file().decls().get(1);
        FuncDecl get = (FuncDecl) p.members().get(1);
        Attribute target = (Attribute) ((Assign) get.body().get(0)).target();
        assertEquals("n", t.members.get(target).field().name, "cible d'affectation");
    }

    @Test
    void functionParametersAndSelfAreRecorded() {
        TypedProgram t = analyze("class A:", "    def f(self, a: int, b: str = \"x\") -> int:", "        return a",
                "def g(n: float):", "    pass").typed();
        ClassDecl a = (ClassDecl) t.units.get(0).file().decls().get(0);
        TypedProgram.FuncInfo fi = t.funcs.get((FuncDecl) a.members().get(0));
        assertEquals("self", fi.self().name);
        assertEquals(List.of("a", "b"), fi.params().stream().map(v -> v.name).toList());
        TypedProgram.FuncInfo gi = t.funcs.get(func(t, "g"));
        assertEquals(null, gi.self());
        assertEquals("float", gi.params().get(0).type.toString());
    }

    @Test
    void nameResolutionIsRecorded() {
        TypedProgram t = analyze("def f() -> int:", "    return 1", "def main():", "    x = 5", "    y = x + f()").typed();
        FuncDecl m = func(t, "main");
        Binary add = (Binary) assignedValue(m, 1);
        Name x = (Name) add.left();
        assertEquals(t.declared.get(m.body().get(0)).get(0), t.names.get(x), "x désigne la variable déclarée");
    }

    @Test
    void lambdaCapturesAreRecorded() {
        TypedProgram t = analyze("def f(k: int) -> int:", "    total = 0", "    base = 10",
                "    g = (x: int) -> x + k + base", "    h = (y: int) -> total + y", "    total = 5", "    return g(1) + h(2)",
                "def main():", "    print(f(1))").typed();
        FuncDecl f = func(t, "f");
        var g = t.lambdas.get((Lambda) assignedValue(f, 2));
        assertEquals(List.of("x"), g.params().stream().map(v -> v.name).toList());
        assertEquals(List.of("k", "base"), g.captured().stream().map(v -> v.name).toList(), "k est un paramètre, base une variable");
        var h = t.lambdas.get((Lambda) assignedValue(f, 3));
        assertEquals(List.of("total"), h.captured().stream().map(v -> v.name).toList());
        // seule une variable capturée ET réaffectée vit dans une boîte (base est copiée)
        assertEquals(List.of("total"), t.capturedVars.stream().filter(t::isBoxed).map(v -> v.name).toList());
    }

    @Test
    void nestedLambdaCapturesPropagateToTheEnclosingLambda() {
        TypedProgram t = analyze("def main():", "    n = 3", "    outer = (a: int) -> ((b: int) -> a + b + n)",
                "    print(outer(1)(2))").typed();
        FuncDecl m = func(t, "main");
        Lambda outer = (Lambda) assignedValue(m, 1);
        var info = t.lambdas.get(outer);
        assertEquals(List.of("n"), info.captured().stream().map(v -> v.name).toList());
        var inner = t.lambdas.get((Lambda) outer.body());
        assertEquals(List.of("a", "n"), inner.captured().stream().map(v -> v.name).toList());
    }
}
