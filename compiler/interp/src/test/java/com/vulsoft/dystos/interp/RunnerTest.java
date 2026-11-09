package com.vulsoft.dystos.interp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunnerTest {

    private static final Path EXAMPLES = Path.of("src/test/resources/exemples");

    private static String src(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    private static Runner.Result run(String... lines) {
        return Runner.run(src(lines), Runner.Options.sandbox());
    }

    /** Exécute et exige un succès ; retourne la sortie standard. */
    private static String out(String... lines) {
        Runner.Result r = run(lines);
        assertEquals(0, r.exitCode(), "stderr=" + r.stderr() + " diagnostics=" + r.diagnostics());
        return r.stdout();
    }

    // ---------------- Les 20 exemples de la Phase 0 ----------------

    private static String example(String prefix) throws IOException {
        try (var s = Files.list(EXAMPLES)) {
            Path p = s.filter(x -> x.getFileName().toString().startsWith(prefix)).sorted().findFirst().orElseThrow();
            return Files.readString(p);
        }
    }

    private static void expect(String prefix, String expected) throws IOException {
        Path work = Files.createTempDirectory("dystos-test");
        Runner.Result r = Runner.run(example(prefix), Runner.Options.sandbox().withFiles(work));
        assertEquals(0, r.exitCode(), prefix + " : stderr=" + r.stderr() + " " + r.diagnostics());
        assertEquals(expected, r.stdout(), prefix);
    }

    @Test
    void example01to04() throws IOException {
        expect("01", "Bonjour le monde\n");
        expect("02", "Lunkht a 31 ans, 1.75 m, actif=true\n");
        expect("03", "Bonjour M. Diallo\nBonjour Dr Martin\n");
        expect("04", "1\n2\nFizz\n4\nBuzz\nFizz\n7\n8\nFizz\nBuzz\n11\nFizz\n13\n14\nFizzBuzz\n1\n2\n4\n5\n");
    }

    @Test
    void example05to08() throws IOException {
        expect("05", "pomme 3\npomme: 3\npoire: 5\nkiwi: 7\n3\n");
        expect("06", "[0, 4, 16, 36, 64] {\"a\": 1, \"bcd\": 3, \"ef\": 2} {0, 2, 4}\n");
        expect("07", "DÉVELOPPEUR 11 Déve\n3.14 | 00042 | ok\na-b-c\n");
        expect("08", "Compte(Awa, 150.0)\n");
    }

    @Test
    void example09to14() throws IOException {
        expect("09", "13.0\n");
        expect("10", "42 x\n");
        expect("12", "Erreur : Il manque 15.0\nFin\n");
        expect("13", "[2, 4, 6] [2, 4, 6] [\"a\", \"bb\", \"ccc\"]\n");
        expect("14", "week-end\n");
    }

    @Test
    void example15to20() throws IOException {
        expect("15", "ligne 1\nligne 2\n");
        expect("17", "[0, 1, 4, 9]\n");
        expect("19", "");
        expect("20", "riz\nintrouvable\nValeur totale : 278.00\n");
        Path work = Files.createTempDirectory("dystos-test");
        Runner.Result r = Runner.run(example("16"), Runner.Options.sandbox().withFiles(work));
        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().matches("2 \\d{4}\n"), r.stdout());
    }

    @Test
    void example11IsRefusedBeforeRunning() throws IOException {
        Runner.Result r = Runner.run(example("11"), Runner.Options.sandbox());
        assertEquals(2, r.exitCode());
        assertEquals("", r.stdout());
        assertEquals("E0201", r.diagnostics().get(0).code());
    }

    @Test
    void example18AcrossTwoModules() throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("app.outils", example("18-packages-et-visibilite-a"));
        m.put("main", src("import app.outils", "", "def main():", "    print(outils.public())"));
        Runner.Result ok = Runner.runModules(m, "main", Runner.Options.sandbox());
        assertEquals(0, ok.exitCode(), ok.stderr() + ok.diagnostics());
        assertEquals("43\n", ok.stdout());

        m.put("main", example("18-packages-et-visibilite-b"));
        Runner.Result bad = Runner.runModules(m, "main", Runner.Options.sandbox());
        assertEquals(2, bad.exitCode());
        assertEquals("E0104", bad.diagnostics().get(0).code());
    }

    @Test
    void example19RunsAsTests() throws IOException {
        Runner.TestReport rep = Runner.runTests(example("19"), Runner.Options.sandbox());
        assertEquals(1, rep.results().size());
        assertTrue(rep.allPassed(), rep.results().toString());
        Runner.TestReport bad = Runner.runTests(src("@test", "def t():", "    assert 1 == 2, \"non\""), Runner.Options.sandbox());
        assertTrue(!bad.allPassed());
        assertTrue(bad.results().get(0).message().contains("non"), bad.results().get(0).message());
    }

    // ---------------- Nombres et formats ----------------

    @Test
    void numberSemantics() {
        assertEquals("2.5\n1\n2\n1024\n0.5\n0.30000000000000004\n", out(
                "print(10 / 4)", "print(7 % 3)", "print(-7 % 3)", "print(2 ** 10)", "print(2.0 ** -1)", "print(0.1 + 0.2)"));
        assertEquals("exposant\n", out("try:", "    x = 2 ** -1", "except RuntimeError:", "    print(\"exposant\")"));
        assertEquals("1e+20\n0.0001\n1e-05\n", out("print(1e20)", "print(0.0001)", "print(0.00001)"));
    }

    @Test
    void intWidensToFloatAtDeclaredTypes() {
        assertEquals("2.0\n3.0\n4.0\n", out(
                "def f(x: float) -> float:", "    return x", "def g() -> float:", "    return 4",
                "print(f(2))", "v: float = 3", "print(v)", "print(g())"));
    }

    @Test
    void formatSpecs() {
        assertEquals("3.14\nff\n    42\n**a**\n1,234,567\n25.6%\n+5\n00042\nab   |\n", out(
                "print(f\"{3.14159:.2f}\")", "print(f\"{255:x}\")", "print(f\"{42:>6}\")", "print(f\"{'a':*^5}\")",
                "print(f\"{1234567:,}\")", "print(f\"{0.256:.1%}\")", "print(f\"{5:+d}\")", "print(f\"{42:05d}\")",
                "print(f\"{'ab':<5}|\")"));
    }

    @Test
    void integerOverflowAndDivisionByZeroAreRuntimeErrors() {
        assertEquals("débordement\nzéro\n", out(
                "try:", "    x = 9223372036854775807 + 1", "except RuntimeError:", "    print(\"débordement\")",
                "try:", "    y = 1 / 0", "except RuntimeError as e:", "    print(\"zéro\")"));
    }

    // ---------------- Valeurs et collections ----------------

    @Test
    void stringsListsAndMaps() {
        assertEquals("e\n3\n[2, 3]\n[3, 2, 1]\nb\n[\"x\", \"y\"]\ntrue\n", out(
                "s = \"abcde\"", "print(s[-1])", "xs = [1, 2, 3]", "print(len(xs))", "print(xs[1:])", "print(xs[::-1])",
                "m: map[str, str] = {\"a\": \"b\"}", "print(m[\"a\"])", "print(\"x,y\".split(\",\"))",
                "print(\"a\" in \"cat\")"));
    }

    @Test
    void mapGetReturnsNullForMissingKeys() {
        assertEquals("null\n0\n", out("m: map[str, int] = {\"a\": 1}", "print(m.get(\"z\"))", "print(m.get(\"z\") ?: 0)"));
    }

    @Test
    void indexErrorsAreCatchable() {
        assertEquals("hors limites\nclé\n", out(
                "xs = [1]", "try:", "    print(xs[5])", "except RuntimeError as e:", "    print(\"hors limites\")",
                "m: map[str, int] = {}", "try:", "    print(m[\"k\"])", "except RuntimeError:", "    print(\"clé\")"));
    }

    @Test
    void conversionsAndValueError() {
        assertEquals("12\n3\n2.5\nstr:7\nerreur\n", out(
                "print(int(\"12\"))", "print(int(3.9))", "print(float(\"2.5\"))", "print(\"str:\" + str(7))",
                "try:", "    int(\"abc\")", "except ValueError:", "    print(\"erreur\")"));
    }

    @Test
    void comprehensionsAndBuiltins() {
        assertEquals("[0, 2, 4]\n{1: 1, 2: 4}\n[\"a\", \"bb\"]\n6\n3\n[0, 1]\n", out(
                "print([x for x in range(5) if x % 2 == 0])", "print({x: x * x for x in range(1, 3)})",
                "print(sorted([\"bb\", \"a\"], key=s -> s.length))", "print(sum([1, 2, 3]))", "print(max(1, 3))",
                "m: map[str, int] = {\"a\": 0, \"b\": 1}", "print([v for k, v in m.items()])"));
    }

    // ---------------- Fonctions et classes ----------------

    @Test
    void closuresAndLambdas() {
        assertEquals("11\n", out(
                "def adder(n: int) -> (int) -> int:", "    return x -> x + n", "f = adder(10)", "print(f(1))"));
    }

    @Test
    void inheritanceSuperAndStatics() {
        assertEquals("Chat dit miaou\nbonjour\n<Rien>\n", out(
                "class Animal:", "    nom: str", "    def init(self, nom: str):", "        self.nom = nom",
                "    def parler(self) -> str:", "        return \"...\"", "    def presenter(self) -> str:",
                "        return self.nom + \" dit \" + self.parler()",
                "class Chat(Animal):", "    def parler(self) -> str:", "        return \"miaou\"",
                "class Outil:", "    @static", "    def salut() -> str:", "        return \"bonjour\"",
                "class Rien:", "    pass",
                "print(Chat(\"Chat\").presenter())", "print(Outil.salut())", "print(Rien())"));
    }

    @Test
    void superMethodCallAndInterfaceDefaults() {
        assertEquals("A+B\nsalut\n", out(
                "class A:", "    def f(self) -> str:", "        return \"A\"",
                "class B(A):", "    def f(self) -> str:", "        return super().f() + \"+B\"",
                "interface Politesse:", "    def nom(self) -> str", "    def saluer(self) -> str:", "        return \"salut\"",
                "class P(Politesse):", "    def nom(self) -> str:", "        return \"p\"",
                "print(B().f())", "print(P().saluer())"));
    }

    @Test
    void enumsAndMatch() {
        assertEquals("Couleur.ROUGE\n0\nrouge\nautre\n", out(
                "enum Couleur:", "    ROUGE, VERT",
                "def nom(c: Couleur) -> str:", "    match c:", "        case Couleur.ROUGE:", "            return \"rouge\"",
                "        case _:", "            return \"autre\"",
                "print(Couleur.ROUGE)", "print(Couleur.ROUGE.ordinal)", "print(nom(Couleur.ROUGE))", "print(nom(Couleur.VERT))"));
    }

    @Test
    void matchOnLiterals() {
        assertEquals("un\nautre\n", out(
                "def f(n: int) -> str:", "    match n:", "        case 1 | 2:", "            return \"un\"",
                "        case _:", "            return \"autre\"", "print(f(2))", "print(f(9))"));
    }

    @Test
    void scriptsRunTopLevelStatements() {
        assertEquals("0\n1\n2\n6\n", out("def double(n: int) -> int:", "    return n * 2", "total = 0",
                "for i in range(3):", "    total += i", "    print(i)", "print(double(total))"));
    }

    // ---------------- Exceptions ----------------

    @Test
    void finallyRunsAndReturnPassesThrough() {
        assertEquals("try\nfinally\n5\n", out(
                "def f() -> int:", "    try:", "        print(\"try\")", "        return 5", "    finally:",
                "        print(\"finally\")", "print(f())"));
    }

    @Test
    void customExceptionsAndHierarchy() {
        assertEquals("attrapé: oups\n", out(
                "class MonErreur(RuntimeError):", "    pass",
                "try:", "    raise MonErreur(\"oups\")", "except Exception as e:", "    print(\"attrapé: \" + e.message)"));
    }

    @Test
    void uncaughtExceptionGivesExitCode1() {
        Runner.Result r = run("def main():", "    raise ValueError(\"boom\")");
        assertEquals(1, r.exitCode());
        assertTrue(r.stderr().contains("ValueError") && r.stderr().contains("boom") && r.stderr().contains("ligne 2"), r.stderr());
    }

    @Test
    void withClosesAndFilesStayInWorkDir() throws IOException {
        Path work = Files.createTempDirectory("dystos-test");
        Runner.Options o = Runner.Options.sandbox().withFiles(work);
        Runner.Result r = Runner.run(src("import io", "with io.open(\"a.txt\", \"w\") as f:", "    f.write(\"salut\")",
                "with io.open(\"a.txt\") as g:", "    print(g.read())"), o);
        assertEquals(0, r.exitCode(), r.stderr());
        assertEquals("salut\n", r.stdout());
        assertEquals("salut", Files.readString(work.resolve("a.txt")));
        Runner.Result esc = Runner.run(src("import io", "f = io.open(\"../evil.txt\", \"w\")"), o);
        assertEquals(1, esc.exitCode());
        assertTrue(esc.stderr().contains("hors du répertoire"), esc.stderr());
    }

    @Test
    void concurrentTasksPropagateErrorsAtAwait() {
        assertEquals("[2, 4]\nerreur\n", out(
                "import concurrent", "ts = [concurrent.run(() -> i * 2) for i in range(1, 3)]", "print([t.await() for t in ts])",
                "def boom() -> int:", "    raise RuntimeError(\"x\")", "t = concurrent.run(() -> boom())",
                "try:", "    t.await()", "except RuntimeError:", "    print(\"erreur\")"));
    }

    // ---------------- Limites et bac à sable ----------------

    @Test
    void infiniteLoopIsStopped() {
        Runner.Result r = Runner.run(src("while true:", "    pass"), Runner.Options.sandbox().withMaxSteps(10_000));
        assertEquals(3, r.exitCode());
        assertTrue(r.stderr().contains("instructions"), r.stderr());
    }

    @Test
    void timeoutIsEnforced() {
        Runner.Result r = Runner.run(src("while true:", "    pass"), Runner.Options.sandbox().withMaxSteps(Long.MAX_VALUE).withTimeout(300));
        assertEquals(3, r.exitCode());
        assertTrue(r.durationMillis() < 3000, "durée : " + r.durationMillis());
    }

    @Test
    void outputIsBounded() {
        Runner.Result r = Runner.run(src("while true:", "    print(\"xxxxxxxxxx\")"), Runner.Options.sandbox().withMaxOutput(100));
        assertEquals(3, r.exitCode());
        assertTrue(r.stdout().length() <= 100, "taille : " + r.stdout().length());
    }

    @Test
    void deepRecursionIsAnError() {
        Runner.Result r = run("def f(n: int) -> int:", "    return f(n + 1)", "f(0)");
        assertEquals(1, r.exitCode());
        assertTrue(r.stderr().contains("profondeur"), r.stderr());
        assertEquals("rattrapé\n", out("def f(n: int) -> int:", "    return f(n + 1)", "try:", "    f(0)",
                "except RuntimeError:", "    print(\"rattrapé\")"));
    }

    @Test
    void filesAreDisabledInSandbox() {
        Runner.Result r = run("import io", "f = io.open(\"x.txt\", \"w\")");
        assertEquals(1, r.exitCode());
        assertTrue(r.stderr().contains("désactivé"), r.stderr());
    }

    @Test
    void dangerousJavaClassesCannotBeImported() {
        for (String imp : List.of("java.lang.Runtime", "java.lang.ProcessBuilder", "java.io.File", "java.lang.System",
                "java.net.URL", "java.util.concurrent.Executors", "java.lang.Thread", "java.nio.file.Files")) {
            Runner.Result r = run("import " + imp, "def main():", "    pass");
            assertEquals(2, r.exitCode(), imp);
            assertEquals("E0105", r.diagnostics().get(0).code(), imp);
        }
    }

    // ---------------- Interopérabilité Java ----------------

    @Test
    void javaInterop() {
        assertEquals("3\n5\nabc\n", out(
                "import java.util.ArrayList", "import java.lang.Math", "import java.lang.StringBuilder",
                "xs = ArrayList[int]()", "xs.add(1)", "xs.add(2)", "xs.add(3)", "print(xs.size())",
                "print(Math.max(2, 5))",
                "sb = StringBuilder()", "sb.append(\"a\")", "sb.append(\"bc\")", "print(sb.toString())"));
    }

    // ---------------- Résultat JSON ----------------

    @Test
    void resultJson() {
        String json = run("print(\"a\\\"b\")").toJson();
        assertTrue(json.startsWith("{\"stdout\":\"a\\\"b\\n\",\"stderr\":\"\",\"exitCode\":0,\"durationMs\":"), json);
        assertTrue(json.endsWith(",\"diagnostics\":[]}"), json);
    }

    @Test
    void lexicalAndSyntaxErrorsAreExitCode2() {
        assertEquals(2, run("x = $").exitCode());
        Runner.Result r = run("if x", "    pass");
        assertEquals(2, r.exitCode());
        assertEquals("E0002", r.diagnostics().get(0).code());
    }
}
