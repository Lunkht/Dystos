package com.vulsoft.dystos.sema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SemaTest {

    private static String src(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    private static List<Diagnostic> check(String... lines) {
        return SemanticAnalyzer.check(src(lines)).diagnostics();
    }

    private static List<String> errorCodes(String... lines) {
        return check(lines).stream().filter(Diagnostic::isError).map(Diagnostic::code).collect(Collectors.toList());
    }

    private static void ok(String... lines) {
        List<Diagnostic> errors = check(lines).stream().filter(Diagnostic::isError).collect(Collectors.toList());
        assertTrue(errors.isEmpty(), "aucune erreur attendue, obtenu : " + errors);
    }

    private static void fails(String code, String... lines) {
        List<String> codes = errorCodes(lines);
        assertTrue(codes.contains(code), "erreur " + code + " attendue, obtenu : " + codes + " pour\n" + src(lines));
    }

    private static void failsOnly(String code, String... lines) {
        assertEquals(List.of(code), errorCodes(lines), src(lines));
    }

    // ---------------- Les exemples de la Phase 0 ----------------

    @Test
    void phase0ExamplesAreAccepted() throws IOException {
        Path dir = Path.of("src/test/resources/exemples");
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.toString().endsWith(".dys")).sorted().collect(Collectors.toList());
        }
        int checked = 0;
        for (Path p : files) {
            String name = p.getFileName().toString();
            if (name.startsWith("11-") || name.startsWith("18-")) {
                continue;
            }
            List<Diagnostic> d = SemanticAnalyzer.check(Files.readString(p)).diagnostics();
            assertTrue(d.isEmpty(), name + " : " + d);
            checked++;
        }
        assertEquals(18, checked);
    }

    @Test
    void example11RejectsNullForNonNullableType() throws IOException {
        List<Diagnostic> d = SemanticAnalyzer.check(Files.readString(
                Path.of("src/test/resources/exemples/11-null-safety.dys"))).diagnostics();
        assertEquals(1, d.size());
        assertEquals("E0201", d.get(0).code());
        assertEquals(6, d.get(0).line());
    }

    @Test
    void example18RejectsPrivateMemberAcrossModules() throws IOException {
        Path dir = Path.of("src/test/resources/exemples");
        Map<String, String> m = new LinkedHashMap<>();
        m.put("app.outils", Files.readString(dir.resolve("18-packages-et-visibilite-a.dys")));
        m.put("main", Files.readString(dir.resolve("18-packages-et-visibilite-b.dys")));
        List<Diagnostic> d = SemanticAnalyzer.checkAll(m).diagnostics();
        assertEquals(1, d.size());
        assertEquals("E0104", d.get(0).code());
        assertEquals(6, d.get(0).line());
        assertEquals("main", d.get(0).file());
    }

    // ---------------- Noms ----------------

    @Test
    void unknownNameWithSuggestion() {
        List<Diagnostic> d = check("def main():", "    valeur = 1", "    print(valuer)");
        assertEquals(1, d.size());
        assertEquals("E0101", d.get(0).code());
        assertEquals(3, d.get(0).line());
        assertTrue(d.get(0).message().contains("valeur"), d.get(0).message());
    }

    @Test
    void unknownImport() {
        failsOnly("E0105", "import nope.truc", "def main():", "    pass");
    }

    @Test
    void duplicateDefinitions() {
        fails("E0102", "def f():", "    pass", "def f():", "    pass");
        fails("E0102", "class A:", "    pass", "class A:", "    pass");
    }

    // ---------------- Types de base ----------------

    @Test
    void assignmentMismatch() {
        failsOnly("E0202", "def main():", "    x: int = \"a\"");
    }

    @Test
    void intWidensToFloatButNotTheReverse() {
        ok("def main():", "    x: float = 1");
        failsOnly("E0202", "def main():", "    y: int = 1.5");
    }

    @Test
    void divisionAlwaysGivesFloat() {
        failsOnly("E0202", "def main():", "    z: int = 4 / 2");
        ok("def main():", "    z: float = 4 / 2");
    }

    @Test
    void stringConcatenationNeedsStrings() {
        failsOnly("E0203", "def main():", "    s = \"a\" + 1");
        ok("def main():", "    s = \"a\" + \"b\"");
    }

    @Test
    void conditionsMustBeBool() {
        failsOnly("E0204", "def main():", "    if 1:", "        pass");
        failsOnly("E0204", "def main():", "    while \"a\":", "        pass");
    }

    @Test
    void inferenceFromFirstAssignment() {
        ok("def main():", "    x = 5", "    x = x + 1");
        fails("E0202", "def main():", "    x = 5", "    x = \"a\"");
    }

    @Test
    void nullAndEmptyCollectionsNeedAnnotations() {
        failsOnly("E0207", "def main():", "    x = null");
        failsOnly("E0207", "def main():", "    x = []");
        ok("def main():", "    x: list[int] = []", "    y: map[str, int] = {}");
    }

    @Test
    void unitValueCannotBeStored() {
        fails("E0207", "def main():", "    x = print(1)");
    }

    // ---------------- Immutabilité ----------------

    @Test
    void valAndParametersAreImmutable() {
        failsOnly("E0212", "def main():", "    val x = 1", "    x = 2");
        failsOnly("E0212", "def f(a: int):", "    a = 2");
        ok("def main():", "    var x = 1", "    x = 2", "    x += 1");
    }

    @Test
    void immutableFieldOutsideInit() {
        fails("E0212", "class A:", "    val x: int = 1", "    def f(self):", "        self.x = 2");
        ok("class A:", "    val x: int", "    def init(self, x: int):", "        self.x = x");
    }

    // ---------------- Null-safety ----------------

    @Test
    void memberAccessOnNullableIsRejected() {
        failsOnly("E0201", "def f(s: str?) -> int:", "    return s.length");
        failsOnly("E0201", "def f(s: str?) -> str:", "    return s.upper()");
    }

    @Test
    void safeCallAndElvisAreAccepted() {
        ok("def f(s: str?) -> int:", "    return s?.length ?: 0");
        ok("def f(s: str?) -> str:", "    return s?.upper() ?: \"\"");
    }

    @Test
    void nullableToNonNullIsRejected() {
        failsOnly("E0201", "def f(s: str?):", "    x: str = s");
        failsOnly("E0201", "def f(s: str?) -> str:", "    return s");
    }

    @Test
    void safeCallOnNonNullableIsOnlyAWarning() {
        List<Diagnostic> d = check("def f(s: str) -> int:", "    return s?.length ?: 0");
        assertTrue(d.stream().noneMatch(Diagnostic::isError), d.toString());
        assertTrue(d.stream().anyMatch(x -> x.code().equals("W0201")), d.toString());
    }

    @Test
    void nullComparisonIsAllowed() {
        ok("def f(s: str?) -> bool:", "    return s == null");
    }

    // ---------------- Fonctions et appels ----------------

    @Test
    void returnTypeIsChecked() {
        failsOnly("E0202", "def f() -> int:", "    return \"a\"");
        fails("E0208", "def f():", "    return 1");
        fails("E0208", "def f() -> int:", "    return");
    }

    @Test
    void missingReturnOnSomePath() {
        fails("E0208", "def f(a: bool) -> int:", "    if a:", "        return 1");
        ok("def f(a: bool) -> int:", "    if a:", "        return 1", "    else:", "        return 2");
        ok("def f(a: bool) -> int:", "    if a:", "        return 1", "    raise ValueError(\"non\")");
    }

    @Test
    void argumentCountAndNames() {
        fails("E0205", "def f(a: int) -> int:", "    return a", "def main():", "    f(1, 2)");
        fails("E0205", "def f(a: int) -> int:", "    return a", "def main():", "    f()");
        fails("E0205", "def f(a: int) -> int:", "    return a", "def main():", "    f(b=1)");
        ok("def f(a: int, b: int = 2) -> int:", "    return a + b", "def main():", "    f(1)", "    f(1, b=3)", "    f(b=3, a=1)");
    }

    @Test
    void argumentTypeMismatch() {
        fails("E0202", "def f(a: int) -> int:", "    return a", "def main():", "    f(\"x\")");
    }

    @Test
    void parameterTypesAreRequired() {
        fails("E0304", "def f(a):", "    pass");
    }

    @Test
    void callingNonCallable() {
        fails("E0206", "def main():", "    x = 1", "    x()");
    }

    // ---------------- Génériques et lambdas ----------------

    @Test
    void genericInference() {
        ok("def first[T](xs: list[T]) -> T:", "    return xs[0]", "def main():", "    y: int = first([1, 2])");
        fails("E0202", "def first[T](xs: list[T]) -> T:", "    return xs[0]", "def main():", "    s: str = first([1, 2])");
    }

    @Test
    void genericClassWithExplicitAndInferredArguments() {
        ok("class Boite[T]:", "    v: T", "    def init(self, v: T):", "        self.v = v",
                "def main():", "    a = Boite[int](1)", "    b = Boite(\"x\")", "    n: int = a.v", "    s: str = b.v");
        fails("E0202", "class Boite[T]:", "    v: T", "    def init(self, v: T):", "        self.v = v",
                "def main():", "    a: Boite[int] = Boite(\"x\")");
    }

    @Test
    void lambdaParametersAreInferred() {
        ok("def main():", "    xs = filter(x -> x > 1, [1, 2, 3])", "    n: int = sum(xs)");
        fails("E0209", "def main():", "    filter(x -> x.length > 1, [1, 2])");
        fails("E0207", "def main():", "    f = x -> x");
    }

    @Test
    void functionTypedParameters() {
        ok("def ap(f: (int) -> int, v: int) -> int:", "    return f(v)", "def main():", "    ap(x -> x * 2, 3)");
        fails("E0202", "def ap(f: (int) -> int, v: int) -> int:", "    return f(v)", "def main():", "    ap(x -> \"a\", 3)");
    }

    // ---------------- Classes ----------------

    @Test
    void methodsNeedSelf() {
        fails("E0303", "class A:", "    def f():", "        pass");
        ok("class A:", "    @static", "    def f() -> int:", "        return 1", "def main():", "    n: int = A.f()");
    }

    @Test
    void unknownMembers() {
        fails("E0209", "class A:", "    x: int", "def main():", "    a = A()", "    print(a.y)");
        fails("E0209", "class A:", "    x: int", "def main():", "    a = A()", "    a.go()");
        fails("E0209", "class A:", "    def f(self):", "        self.z = 1");
    }

    @Test
    void privateMembersAreClassLocal() {
        fails("E0104", "class A:", "    _x: int = 1", "def main():", "    a = A()", "    print(a._x)");
        ok("class A:", "    _x: int = 1", "    def get(self) -> int:", "        return self._x");
        fails("E0104", "class A:", "    def _f(self):", "        pass", "def main():", "    A()._f()");
    }

    @Test
    void interfaceMethodsMustBeImplemented() {
        fails("E0301", "interface F:", "    def aire(self) -> float", "class C(F):", "    pass");
        ok("interface F:", "    def aire(self) -> float", "class C(F):", "    def aire(self) -> float:", "        return 1.0");
        ok("interface F:", "    def aire(self) -> float", "abstract class B(F):", "    pass");
    }

    @Test
    void overrideMustBeCompatible() {
        fails("E0302", "class A:", "    def f(self) -> int:", "        return 1",
                "class B(A):", "    def f(self) -> str:", "        return \"a\"");
        fails("E0302", "class A:", "    def f(self, x: int) -> int:", "        return x",
                "class B(A):", "    def f(self) -> int:", "        return 1");
    }

    @Test
    void abstractClassesAndInterfacesCannotBeInstantiated() {
        fails("E0308", "abstract class A:", "    pass", "def main():", "    A()");
        fails("E0308", "interface I:", "    def f(self)", "def main():", "    I()");
    }

    @Test
    void constructorsAreInherited() {
        ok("class A:", "    x: int", "    def init(self, x: int):", "        self.x = x",
                "class B(A):", "    pass", "def main():", "    b = B(3)");
        fails("E0205", "class A:", "    x: int", "    def init(self, x: int):", "        self.x = x",
                "class B(A):", "    pass", "def main():", "    b = B()");
    }

    @Test
    void superInitIsChecked() {
        ok("class A:", "    def init(self, x: int):", "        pass",
                "class B(A):", "    def init(self):", "        super().init(1)");
        fails("E0202", "class A:", "    def init(self, x: int):", "        pass",
                "class B(A):", "    def init(self):", "        super().init(\"a\")");
    }

    @Test
    void inheritanceCycleIsReported() {
        fails("E0305", "class A(B):", "    pass", "class B(A):", "    pass");
    }

    @Test
    void enumsAndMatch() {
        ok("enum C:", "    R, V", "def f(c: C) -> int:", "    match c:", "        case C.R:", "            return 1",
                "        case C.V:", "            return 2");
        fails("E0208", "enum C:", "    R, V", "def f(c: C) -> int:", "    match c:", "        case C.R:", "            return 1");
        fails("E0202", "enum C:", "    R", "def f(c: C):", "    match c:", "        case 1:", "            pass");
    }

    // ---------------- Instructions ----------------

    @Test
    void loopsAndDestructuring() {
        ok("def main():", "    m: map[str, int] = {}", "    for k, v in m.items():", "        print(k, v)",
                "    for i, x in enumerate([1, 2]):", "        print(i, x)");
        fails("E0210", "def main():", "    for a, b in [1, 2]:", "        pass");
        fails("E0210", "def main():", "    for a in 5:", "        pass");
        fails("E0215", "def main():", "    break");
    }

    @Test
    void exceptionsAreChecked() {
        fails("E0213", "def main():", "    raise 1");
        fails("E0213", "def main():", "    try:", "        pass", "    except int:", "        pass");
        ok("def main():", "    try:", "        raise ValueError(\"x\")", "    except ValueError as e:", "        print(e.message)");
    }

    @Test
    void withNeedsClose() {
        fails("E0214", "def main():", "    with 1 as x:", "        pass");
        ok("import io", "def main():", "    with io.open(\"a\") as f:", "        f.write(\"x\")");
    }

    @Test
    void indexing() {
        ok("def main():", "    xs = [1, 2]", "    a: int = xs[0]", "    b = xs[0:1]", "    s = \"ab\"", "    c: str = s[0]");
        fails("E0202", "def main():", "    xs = [1, 2]", "    a = xs[\"a\"]");
        fails("E0211", "def main():", "    x = 1", "    y = x[0]");
    }

    // ---------------- Scripts ----------------

    @Test
    void scriptsAreAccepted() {
        ok("x = 1", "print(x)");
        ok("def double(n: int) -> int:", "    return n * 2", "print(double(3))");
        ok("total = 0", "for i in range(3):", "    total += i", "print(total)");
    }

    @Test
    void scriptAndMainTogetherAreAmbiguous() {
        failsOnly("E0307", "x = 1", "def main():", "    pass");
    }

    // ---------------- Interopérabilité Java ----------------

    @Test
    void javaClassesAreUsable() {
        ok("import java.util.ArrayList", "def main():", "    l = ArrayList[str]()", "    l.add(\"a\")", "    n: int = l.size()");
        ok("import java.time.LocalDate", "def main():", "    y: int = LocalDate.now().year");
        ok("import java.util.*", "def main():", "    l = ArrayList[int]()", "    l.add(1)");
    }

    @Test
    void javaGenericsAreChecked() {
        fails("E0202", "import java.util.ArrayList", "def main():", "    l = ArrayList[str]()", "    l.add(1)");
    }

    @Test
    void javaClassesOutsideAllowedPrefixesAreRefused() {
        failsOnly("E0105", "import com.example.Truc", "def main():", "    pass");
        List<Diagnostic> d = SemanticAnalyzer.analyze(
                List.of(new SemanticAnalyzer.SourceUnit("main", com.vulsoft.dystos.parser.Parser.parse(
                        src("import java.util.ArrayList", "def main():", "    pass")))), List.of()).diagnostics();
        assertTrue(d.stream().anyMatch(x -> x.code().equals("E0105")), d.toString());
    }

    // ---------------- Erreurs lexicales / syntaxiques et JSON ----------------

    @Test
    void syntaxErrorsBecomeDiagnostics() {
        List<Diagnostic> d = SemanticAnalyzer.check("if x\n    pass\n").diagnostics();
        assertEquals(1, d.size());
        assertEquals("E0002", d.get(0).code());
        assertEquals(1, d.get(0).line());
        List<Diagnostic> lex = SemanticAnalyzer.check("x = $\n").diagnostics();
        assertEquals("E0001", lex.get(0).code());
    }

    @Test
    void jsonFormat() {
        SemanticAnalyzer.Result r = SemanticAnalyzer.check(src("def main():", "    x: int = \"a\""));
        String json = r.toJson();
        assertTrue(json.startsWith("[{\"severity\":\"error\",\"code\":\"E0202\""), json);
        assertTrue(json.contains("\"line\":2"), json);
        assertTrue(json.contains("\\\"") || json.contains("'int'"), json);
        assertEquals("[]", SemanticAnalyzer.check("def main():\n    pass\n").toJson());
    }
}
