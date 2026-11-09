package com.vulsoft.dystos.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vulsoft.dystos.interp.Runner;
import com.vulsoft.dystos.sema.Diagnostic;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

/**
 * Tests différentiels : le même programme est exécuté par l'interpréteur et, compilé en bytecode, par une vraie JVM
 * ({@code java -jar}) ; la sortie standard et le code de sortie doivent être identiques.
 */
class JvmTest {

    private static final Path EXAMPLES = Path.of("src/test/resources/exemples");

    private record Run(String stdout, String stderr, int exit) {
    }

    private static String src(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    // ---- exécution ------------------------------------------------------------------------

    private static Run viaJvm(Map<String, String> sources, String entry) throws Exception {
        Compiler.Output o = Compiler.compile(sources, entry);
        assertTrue(o.ok(), "compilation : " + o.diagnostics());
        Path jar = Files.createTempFile("dystos-test-", ".jar");
        try {
            Compiler.writeJar(o, jar);
            Process p = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Dfile.encoding=UTF-8", "-jar", jar.toString())
                    .directory(Files.createTempDirectory("dystos-cwd-").toFile()).start();
            p.getOutputStream().close();
            var out = java.util.concurrent.CompletableFuture.supplyAsync(() -> read(p.getInputStream()));
            var err = java.util.concurrent.CompletableFuture.supplyAsync(() -> read(p.getErrorStream()));
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new AssertionError("délai dépassé");
            }
            return new Run(out.get(), err.get(), p.exitValue());
        } finally {
            Files.deleteIfExists(jar);
        }
    }

    private static String read(java.io.InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /** Les fins de ligne d'un processus fils dépendent du système (CRLF sous Windows) : on compare sans elles. */
    private static String norm(String s) {
        return s == null ? "" : s.replace("\r\n", "\n");
    }

    private static Run viaJvm(String source) throws Exception {
        return viaJvm(Map.of("main", source), "main");
    }

    private static Run viaInterpreter(String source) throws IOException {
        Runner.Result r = Runner.run(source, Runner.Options.trusted(Files.createTempDirectory("dystos-jvm-test")));
        return new Run(r.stdout(), r.stderr(), r.exitCode());
    }

    /** Le programme doit se comporter de la même façon avec l'interpréteur et avec le bytecode. */
    private static Run same(String source) throws Exception {
        Run expected = viaInterpreter(source);
        Run actual = viaJvm(source);
        assertEquals(norm(expected.stdout()), norm(actual.stdout()), "sortie standard différente pour :\n" + source + "stderr JVM : " + actual.stderr());
        assertEquals(expected.exit(), actual.exit(), "code de sortie différent pour :\n" + source + "stderr JVM : " + actual.stderr());
        return actual;
    }

    private static Run same(String... lines) throws Exception {
        return same(src(lines));
    }

    /** Comme {@link #same(String)} mais compare aussi la sortie d'erreur (message et ligne de l'erreur non rattrapée). */
    private static Run sameWithStderr(String... lines) throws Exception {
        String source = src(lines);
        Run expected = viaInterpreter(source);
        Run actual = same(source);
        assertEquals(norm(expected.stderr()), norm(actual.stderr()), "sortie d'erreur différente pour :\n" + source);
        return actual;
    }

    private static String example(String prefix) throws IOException {
        try (var s = Files.list(EXAMPLES)) {
            return Files.readString(s.filter(x -> x.getFileName().toString().startsWith(prefix)).sorted().findFirst().orElseThrow());
        }
    }

    // ---- les exemples de la Phase 0 -----------------------------------------------------------------

    @Test
    void examplesGiveTheSameOutput() throws Exception {
        for (String p : List.of("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "12", "13", "14", "15", "16", "17", "19", "20")) {
            same(example(p));
        }
    }

    @Test
    void everyExampleCompilesToBytecode() throws Exception {
        try (var files = Files.list(EXAMPLES)) {
            for (Path f : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".dys")
                    && !x.getFileName().toString().startsWith("11-") && !x.getFileName().toString().startsWith("18-"))
                    .sorted()::iterator) {
                Compiler.Output o = Compiler.compile(Files.readString(f));
                assertTrue(o.ok(), f.getFileName() + " : " + o.diagnostics());
            }
        }
    }

    @Test
    void semanticErrorsAreReportedBeforeGeneration() {
        Compiler.Output o = Compiler.compile(src("def main():", "    x: int = \"a\""));
        assertTrue(!o.ok());
        assertEquals("E0202", o.diagnostics().get(0).code());
        assertTrue(o.classes().isEmpty());
        assertEquals("E0002", Compiler.compile("if x\n    pass\n").diagnostics().get(0).code());
        assertEquals("E0001", Compiler.compile("x = $\n").diagnostics().get(0).code());
    }

    // ---- nombres ----------------------------------------------------------------------------------------

    @Test
    void numbers() throws Exception {
        same("print(7 / 2, 7 % 3, -7 % 3, 2 ** 10, 2.0 ** -1, 7.5 % 2, 3 * 1.5, 10 - 4)",
                "print(abs(-3), abs(-2.5), min(3, 1), max(2.5, 1), 0.1 + 0.2, 1e20, 0.0001, 10 / 4)",
                "x = 5", "x += 2", "x *= 3", "x -= 1", "print(x)", "y = 7.0", "y /= 2", "print(y)",
                "print(1 < 2, 2.5 >= 2, 3 == 3.0, 1 != 2, not (1 > 2))");
    }

    @Test
    void integerErrorsAreCatchable() throws Exception {
        same("try:", "    x = 9223372036854775807 + 1", "except RuntimeError as e:", "    print(e.message)",
                "try:", "    print(1 / 0)", "except RuntimeError as e:", "    print(e.message)",
                "try:", "    print(5 % 0)", "except RuntimeError as e:", "    print(e.message)",
                "try:", "    print(2 ** -1)", "except RuntimeError as e:", "    print(e.message)");
    }

    @Test
    void intWidensToFloatInCollectionsOnTheJvm() throws Exception {
        // Amélioration par rapport à l'interpréteur : le backend connaît les types statiques.
        Run r = viaJvm(src("xs: list[float] = [1, 2.5]", "print(xs)", "xs.add(3)", "print(xs, sum(xs))",
                "m: map[str, float] = {\"a\": 1}", "print(m)", "def f(x: float) -> float:", "    return x", "print(f(2))"));
        assertEquals("[1.0, 2.5]\n[1.0, 2.5, 3.0] 6.5\n{\"a\": 1.0}\n2.0\n", r.stdout());
    }

    // ---- texte -----------------------------------------------------------------------------------------------

    @Test
    void stringsAndFormats() throws Exception {
        same("s = \"  Développeur  \"", "print(s.strip().upper(), s.lower(), s.strip().length, len(s))",
                "print(s.strip()[0:4], s.strip()[-3:], s.strip()[::-1], s.strip()[2])",
                "print(\"a,b,c\".split(\",\"), \"-\".join([\"x\", \"y\"]), \"abc\".replace(\"b\", \"B\"))",
                "print(\"ab\".startswith(\"a\"), \"ab\".endswith(\"x\"), \"b\" in \"abc\", \"a\" < \"b\", \"b\" >= \"b\")",
                "print(f\"{3.14159:.2f}|{255:x}|{42:>6}|{'a':*^5}|{1234567:,}|{0.256:.1%}|{5:+d}|{42:05d}\")",
                "name = \"Awa\"", "n = 3", "print(f\"{name} a {n} ans, {n * 1.5} ... {n > 2} {'ok' if n > 2 else 'ko'}\")");
    }

    // ---- collections --------------------------------------------------------------------------------------------

    @Test
    void lists() throws Exception {
        same("xs = [3, 1, 2]", "xs.add(5)", "print(xs, len(xs), xs[0], xs[-1], xs[1:3], xs[::-1])",
                "print(sorted(xs), sum(xs), 2 in xs, 9 in xs, xs.contains(1))",
                "print(xs.pop(), xs)", "xs.extend([7, 8])", "xs[0] = 10", "xs[1] += 5", "print(xs)",
                "xs.clear()", "print(xs, len(xs))",
                "for i, x in enumerate([\"a\", \"b\"]):", "    print(i, x)",
                "print(range(3), range(2, 5), range(10, 0, -3))");
    }

    @Test
    void mapsAndSets() throws Exception {
        same("m: map[str, int] = {\"a\": 1, \"b\": 2}", "m[\"c\"] = 3", "m[\"a\"] += 10", "print(m, len(m), m[\"a\"])",
                "print(m.get(\"zz\"), m.get(\"b\"), m.contains(\"a\"), \"c\" in m, m.keys(), m.values())",
                "m.remove(\"b\")", "for k, v in m.items():", "    print(k, v)",
                "s = {3, 1, 3}", "s.add(7)", "s.remove(1)", "print(s, len(s), 7 in s, s.contains(9))",
                "for k in m:", "    print(k)");
    }

    @Test
    void entriesPrintLikeTheInterpreter() throws Exception {
        same("m: map[str, int] = {\"a\": 1}", "print(m.items(), enumerate([\"x\", \"y\"]))",
                "for e in m.items():", "    print(e)");
    }

    @Test
    void comprehensions() throws Exception {
        same("print([x * x for x in range(6) if x % 2 == 0])",
                "print({x % 3 for x in range(10)})",
                "print({k: len(k) for k in [\"a\", \"bcd\", \"ef\"]})",
                "print([a + b for a in range(3) for b in range(3) if a != b])",
                "m: map[str, int] = {\"x\": 1, \"y\": 2}", "print([v * 2 for k, v in m.items()])",
                "print(sum(x for x in range(5)), [c for c in \"abc\"])");
    }

    @Test
    void indexErrorsAreCatchable() throws Exception {
        same("xs = [1]", "try:", "    print(xs[5])", "except RuntimeError as e:", "    print(e.message)",
                "m: map[str, int] = {}", "try:", "    print(m[\"k\"])", "except RuntimeError as e:", "    print(e.message)",
                "try:", "    print(\"abc\"[9])", "except RuntimeError as e:", "    print(e.message)",
                "try:", "    int(\"x\")", "except ValueError as e:", "    print(e.message)");
    }

    // ---- contrôle -------------------------------------------------------------------------------------------------

    @Test
    void loopsAndConditions() throws Exception {
        same("total = 0", "for i in range(10):", "    if i == 7:", "        break", "    if i % 2 == 0:", "        continue",
                "    total += i", "print(total)",
                "n = 0", "while true:", "    n += 1", "    if n > 4:", "        break", "print(n)",
                "for i in range(2, 11, 4):", "    print(i)",
                "for c in \"ab\":", "    print(c)",
                "a = 3", "if a > 5:", "    print(\"grand\")", "elif a > 2 and a < 4:", "    print(\"moyen\")", "else:", "    print(\"petit\")",
                "print(1 if a == 3 else 2)");
    }

    @Test
    void shortCircuitEvaluation() throws Exception {
        same("def t(s: str) -> bool:", "    print(s)", "    return true", "def f(s: str) -> bool:", "    print(s)", "    return false",
                "print(f(\"a\") and t(\"b\"))", "print(t(\"c\") or f(\"d\"))", "print(f(\"e\") or t(\"f\"))",
                "if t(\"g\") and not f(\"h\"):", "    print(\"ok\")");
    }

    @Test
    void matchStatements() throws Exception {
        same("def f(n: int) -> str:", "    match n:", "        case 1 | 2:", "            return \"petit\"",
                "        case 3:", "            return \"trois\"", "        case _:", "            return \"autre\"",
                "def g(s: str) -> int:", "    match s:", "        case \"a\":", "            return 1", "        case _:", "            return 0",
                "print(f(1), f(2), f(3), f(9), g(\"a\"), g(\"b\"))");
    }

    // ---- fonctions -------------------------------------------------------------------------------------------------------

    @Test
    void functionsDefaultsAndNamedArguments() throws Exception {
        same("def f(a: int, b: int = 2, c: str = \"x\") -> str:", "    return f\"{a}-{b}-{c}\"",
                "print(f(1), f(1, 5), f(1, c=\"z\"), f(c=\"q\", a=9), f(1, b=3, c=\"w\"))",
                "def fib(n: int) -> int:", "    if n < 2:", "        return n", "    return fib(n - 1) + fib(n - 2)",
                "print(fib(15))",
                "def half(n: int) -> float:", "    return n / 2", "print(half(3))",
                "def wide(x: float) -> float:", "    return x * 2", "print(wide(4))");
    }

    @Test
    void nullSafety() throws Exception {
        same("def length(s: str?) -> int:", "    return s?.length ?: 0",
                "def up(s: str?) -> str:", "    return s?.upper() ?: \"?\"",
                "print(length(\"abc\"), length(null), up(\"x\"), up(null))",
                "x: int? = null", "print(x ?: 5)", "y: int? = 3", "print(y ?: 0, y)", "x = 8", "print(x ?: 0)",
                "def find(n: int) -> int?:", "    if n > 0:", "        return n", "    return null",
                "print(find(4), find(-1), find(4) ?: 100, find(-1) ?: 100)",
                "z: str? = null", "print(z == null, z != null, \"a\" == z)");
    }

    // ---- fonctions comme valeurs, lambdas, fermetures --------------------------------------------------

    @Test
    void functionValuesAndHigherOrderFunctions() throws Exception {
        same("def apply(f: (int) -> int, x: int) -> int:", "    return f(x)",
                "def double(x: int) -> int:", "    return x * 2",
                "def twice(f: (int) -> int) -> (int) -> int:", "    return (x: int) -> f(f(x))",
                "def make_adder(n: int) -> (int) -> int:", "    return (x: int) -> x + n",
                "def map_list[T, U](xs: list[T], f: (T) -> U) -> list[U]:", "    return [f(x) for x in xs]",
                "def main():",
                "    print(apply(double, 4), apply((x: int) -> x + 1, 4), twice(double)(3), make_adder(10)(5))",
                "    add5 = make_adder(5)", "    print(add5(1), add5(2))",
                "    print(map_list([1, 2], x -> f\"n{x}\"), map_list([\"a\", \"bb\"], s -> s.length))",
                "    ops = [double, (x: int) -> x + 1]", "    print(ops[0](5), ops[1](5))");
    }

    @Test
    void closuresShareReassignedVariables() throws Exception {
        same("def main():", "    total = 0", "    show = () -> total", "    total = 5", "    print(show())",
                "    total += 3", "    print(show())", "    for i in range(3):", "        total += i", "    print(show())",
                "    ratio = 0.5", "    scale = (v: float) -> v * ratio", "    ratio = 2.0", "    print(scale(3.0))",
                "    label = \"a\"", "    name = () -> label + \"!\"", "    label = \"b\"", "    print(name())",
                "    flag = false", "    check = () -> flag", "    flag = true", "    print(check())");
    }

    @Test
    void closuresCopyVariablesThatAreNeverReassigned() throws Exception {
        same("def main():", "    base = 10", "    k = 3", "    g = (x: int) -> x + k + base", "    print(g(1))",
                "    fs = [(n: int) -> n * i for i in range(3)]", "    print([f(10) for f in fs])",
                "    outer = (a: int) -> ((b: int) -> a + b + k)", "    print(outer(1)(2))");
    }

    @Test
    void lambdasCaptureSelfAndFields() throws Exception {
        same("class Compteur:", "    n: int = 0", "    def lire(self) -> () -> int:", "        return () -> self.n",
                "    def plus(self, d: int) -> (int) -> int:", "        return (x: int) -> x + self.n + d",
                "def main():", "    c = Compteur()", "    lire = c.lire()", "    plus = c.plus(100)", "    c.n = 7",
                "    print(lire(), plus(1))");
    }

    @Test
    void sortedAndFilterWithFunctions() throws Exception {
        same("def longueur(s: str) -> int:", "    return s.length",
                "def main():", "    limit = 2", "    print(filter(x -> x > limit, [1, 2, 3, 4]))",
                "    print(sorted([3, 1, 2], key=x -> -x), sorted([\"bb\", \"a\", \"ccc\"], key=longueur))",
                "    print(sorted([\"bb\", \"a\"], s -> s.length), sorted([3, 1, 2]))",
                "    print(filter(longueur_est_pair, [\"ab\", \"c\"]))",
                "def longueur_est_pair(s: str) -> bool:", "    return s.length % 2 == 0");
    }

    @Test
    void functionsWithoutResultAsValues() throws Exception {
        same("def each(xs: list[int], f: (int) -> unit):", "    for x in xs:", "        f(x)",
                "def main():", "    each([1, 2], x -> print(\"v\", x))", "    acc: list[int] = []",
                "    each([3, 4], x -> acc.add(x * 10))", "    print(acc)");
    }

    @Test
    void constructorArgumentsContainingLoopsAreEvaluatedFirst() throws Exception {
        // Un objet non initialisé ne doit pas être sur la pile pendant une boucle (règle du vérificateur ancien).
        same("class Boite:", "    xs: list[int]", "    def init(self, xs: list[int]):", "        self.xs = xs",
                "class Etiquette(Boite):", "    nom: str", "    def init(self, nom: str):",
                "        super().init([c.length for c in [nom, nom + \"!\"]])", "        self.nom = nom",
                "def main():", "    b = Boite([x * 2 for x in range(4)])", "    print(b.xs)",
                "    e = Etiquette(\"ab\")", "    print(e.xs, e.nom)");
    }

    @Test
    void lambdaClassesAreVerifiedStrictly() throws Exception {
        Compiler.Output o = Compiler.compile(src("def make(n: int) -> (int) -> int:", "    return (x: int) -> x + n",
                "def main():", "    total = 0", "    f = () -> total", "    total = 4", "    print(make(2)(f()))"));
        assertTrue(o.ok(), o.diagnostics().toString());
        Path jar = Files.createTempFile("dystos-strict-", ".jar");
        try {
            Compiler.writeJar(o, jar);
            Process p = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Xverify:all", "-jar", jar.toString()).start();
            assertEquals("6\n", new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(0, p.waitFor());
        } finally {
            Files.deleteIfExists(jar);
        }
        assertTrue(o.classes().keySet().stream().anyMatch(k -> k.contains("$Lambda")), "classes de lambdas générées");
    }

    // ---- modules io et concurrent -----------------------------------------------------------------------------------

    @Test
    void filesFollowTheInterpreterRules() throws Exception {
        same("import io", "def main():",
                "    with io.open(\"notes.txt\", \"w\") as f:", "        f.write(\"ligne 1\\nligne 2\\n\")",
                "    with io.open(\"notes.txt\", \"a\") as f:", "        f.write(\"ligne 3\\n\")",
                "    with io.open(\"notes.txt\") as f:", "        print(f.read().length)",
                "    with io.open(\"notes.txt\", mode=\"r\") as f:", "        for l in f.lines():", "            print(l.strip())",
                "    try:", "        io.open(\"absent.txt\")", "    except RuntimeError as e:", "        print(e.message)",
                "    try:", "        io.open(\"../x.txt\", \"w\")", "    except RuntimeError as e:", "        print(e.message)",
                "    try:", "        io.open(\"a.txt\", \"z\")", "    except RuntimeError as e:", "        print(e.message)",
                "    g = io.open(\"n2.txt\", \"w\")", "    g.write(\"x\")", "    g.close()",
                "    try:", "        g.write(\"y\")", "    except RuntimeError as e:", "        print(e.message)",
                "    with io.open(\"n2.txt\") as h:", "        try:", "            h.write(\"z\")", "        except RuntimeError as e:",
                "            print(e.message)", "        print(h.read())");
    }

    @Test
    void filesAreWrittenOnCloseEvenWhenTheBodyFails() throws Exception {
        same("import io", "def main():", "    try:", "        with io.open(\"j.txt\", \"w\") as f:", "            f.write(\"partiel\")",
                "            raise ValueError(\"x\")", "    except ValueError:", "        print(\"erreur\")",
                "    with io.open(\"j.txt\") as f:", "        print(f.read())");
    }

    @Test
    void concurrentTasks() throws Exception {
        same("import concurrent", "def calcul(n: int) -> int:", "    return n * n",
                "def boom(n: int) -> int:", "    raise ValueError(f\"tâche {n}\")",
                "def main():", "    taches = [concurrent.run(() -> calcul(i)) for i in range(5)]",
                "    print([t.await() for t in taches], taches[2].await())",
                "    ko = concurrent.run(() -> boom(7))", "    try:", "        ko.await()", "    except ValueError as e:",
                "        print(e.message)", "    try:", "        ko.await()", "    except ValueError as e:", "        print(\"encore\", e.message)");
    }

    // ---- interopérabilité Java ---------------------------------------------------------------------------------------

    @Test
    void javaCollectionsStringBuildersAndMath() throws Exception {
        same("import java.util.ArrayList", "import java.util.HashMap", "import java.lang.StringBuilder", "import java.lang.Math",
                "def main():",
                "    xs = ArrayList[int]()", "    xs.add(3)", "    xs.add(1)", "    xs.add(2)",
                "    print(xs.size(), xs.get(0) + 1, xs, xs.contains(2))",
                "    m = HashMap[str, int]()", "    m.put(\"a\", 1)", "    m.put(\"b\", 2)",
                "    print(m.get(\"a\"), m.containsKey(\"b\"), m.size())",
                "    sb = StringBuilder()", "    sb.append(\"ab\")", "    sb.append(12)", "    print(sb.toString(), sb.length())",
                "    print(Math.max(3, 7), Math.sqrt(16.0), Math.abs(-2.5), Math.floorMod(-7, 3), Math.PI)");
    }

    @Test
    void javaWrapperTypesStaticMethodsAndFields() throws Exception {
        same("import java.lang.Integer", "import java.lang.Long", "import java.lang.Math", "import java.lang.Double",
                "import java.lang.Character", "import java.util.LinkedList",
                "def main():",
                "    print(Integer.parseInt(\"42\") + 1, Long.parseLong(\"9000000000\") + 1, Double.parseDouble(\"2.5\") * 2)",
                "    print(Math.pow(2.0, 10.0), Math.floor(2.7), Math.min(5, 2), Math.round(2.5))",
                "    print(Integer.MAX_VALUE, Integer.toString(255), Integer.toHexString(255), Character.isDigit(\"7\"))",
                "    q = LinkedList[str]()", "    q.add(\"x\")", "    q.addFirst(\"y\")", "    print(q, q.getFirst(), q.peek())",
                "    big = 5000000000", "    print(Math.abs(-big), Math.max(big, 1))");
    }

    @Test
    void javaDatesAndPropertiesFromGetters() throws Exception {
        same("import java.time.LocalDate", "def main():", "    d = LocalDate.of(2024, 2, 29)",
                "    print(d.year, d.monthValue, d.dayOfMonth, d.plusDays(1).toString(), d.isLeapYear())",
                "    print(d.getYear() + 1)");
    }

    @Test
    void javaConstructorArgumentsContainingLoopsAreEvaluatedFirst() throws Exception {
        same("import java.lang.StringBuilder", "def main():",
                "    sb = StringBuilder(len([c for c in \"abc\"]))", "    sb.append(\"ok\")", "    print(sb.toString(), sb.capacity())");
    }

    @Test
    void aJavaNullWhereANonNullValueIsExpectedIsAClearError() throws Exception {
        // Dystos est null-safe : un résultat Java nul utilisé comme int est une erreur (l'interpréteur laisse passer null).
        Run r = viaJvm(src("import java.util.HashMap", "def main():", "    m = HashMap[str, int]()", "    m.put(\"a\", 1)",
                "    print(m.get(\"a\"))", "    print(m.get(\"absent\"))"));
        assertEquals("1\n", r.stdout());
        assertEquals(1, r.exit());
        assertTrue(r.stderr().contains("valeur Java nulle"), r.stderr());
    }

    @Test
    void javaLimitsAreReportedCleanly() {
        for (String code : List.of(
                src("import java.lang.String", "def main():", "    print(String.format(\"%d\", 1))"),
                src("import java.util.ArrayList", "class Mon(ArrayList[int]):", "    pass", "def main():", "    print(Mon().size())"),
                src("import java.util.List", "def main():", "    print(List.of(1, 2))"))) {
            Compiler.Output o = Compiler.compile(code);
            Diagnostic d = o.diagnostics().stream().filter(Diagnostic::isError).findFirst().orElseThrow(() -> new AssertionError(code));
            assertEquals("E0401", d.code(), code + " -> " + d);
            assertTrue(d.line() > 0, "position manquante : " + d);
            assertTrue(o.classes().isEmpty(), code);
        }
    }

    // ---- classes -------------------------------------------------------------------------------------------------------------

    @Test
    void classesInheritanceAndPolymorphism() throws Exception {
        same("interface Forme:", "    def aire(self) -> float", "    def nom(self) -> str",
                "class Base(Forme):", "    couleur: str = \"rouge\"", "    def init(self, c: str):", "        self.couleur = c",
                "    def nom(self) -> str:", "        return \"base\"", "    def aire(self) -> float:", "        return 0.0",
                "class Carre(Base):", "    cote: float", "    def init(self, c: str, cote: float):", "        super().init(c)",
                "        self.cote = cote", "    def aire(self) -> float:", "        return self.cote * self.cote",
                "    def nom(self) -> str:", "        return \"carre/\" + super().nom()",
                "    def toString(self) -> str:", "        return f\"Carre({self.cote}, {self.couleur})\"",
                "class Vide:", "    pass",
                "formes: list[Forme] = [Carre(\"bleu\", 2.0), Base(\"vert\")]",
                "for f in formes:", "    print(f.nom(), f.aire())",
                "print(Carre(\"jaune\", 3.0), Vide(), formes[0] is formes[0])");
    }

    @Test
    void constructorInheritanceFieldsAndStatics() throws Exception {
        same("class A:", "    x: int", "    y: int = 7", "    def init(self, x: int):", "        self.x = x",
                "    @static", "    def make() -> str:", "        return \"statique\"",
                "    def bump(self):", "        self.x += 1", "        self.y *= 2",
                "class B(A):", "    pass",
                "b = B(4)", "b.bump()", "print(b.x, b.y, A.make())",
                "class P:", "    nom: str = \"p\"", "    n: int = 3", "p = P()", "p.n += 1", "print(p.nom, p.n)");
    }

    @Test
    void genericClassesAndFunctions() throws Exception {
        same("class Boite[T]:", "    v: T", "    def init(self, v: T):", "        self.v = v", "    def get(self) -> T:", "        return self.v",
                "def first[T](xs: list[T]) -> T:", "    return xs[0]",
                "a = Boite[int](40)", "b = Boite(\"x\")",
                "print(a.get() + 2, b.get().upper(), first([5, 6]), first([\"q\"]), a.v + 1)");
        // Le backend applique int -> float aux paramètres de type (l'interpréteur affiche encore « 1 »).
        Run r = viaJvm(src("class Boite[T]:", "    v: T", "    def init(self, v: T):", "        self.v = v",
                "    def get(self) -> T:", "        return self.v", "c = Boite[float](1)", "print(c.get(), c.v)"));
        assertEquals("1.0 1.0\n", r.stdout());
    }

    @Test
    void enums() throws Exception {
        same("enum Couleur:", "    ROUGE, VERT, BLEU",
                "def nom(c: Couleur) -> str:", "    match c:", "        case Couleur.ROUGE:", "            return \"r\"",
                "        case Couleur.VERT:", "            return \"v\"", "        case _:", "            return \"autre\"",
                "c = Couleur.VERT", "print(c, c.name, c.ordinal, nom(c), nom(Couleur.BLEU), c == Couleur.VERT, c is Couleur.ROUGE)");
    }

    // ---- exceptions -------------------------------------------------------------------------------------------------------------------

    @Test
    void exceptionsAndHierarchy() throws Exception {
        same("class Erreur(RuntimeError):", "    pass",
                "class Precise(Erreur):", "    code: int", "    def init(self, code: int):", "        super().init(f\"code {code}\")", "        self.code = code",
                "def boom(n: int):", "    if n == 1:", "        raise Precise(404)", "    if n == 2:", "        raise Erreur(\"simple\")", "    if n == 3:", "        raise ValueError(\"val\")",
                "for i in range(1, 5):", "    try:", "        boom(i)", "        print(\"rien\")",
                "    except Precise as p:", "        print(\"precise\", p.code, p.message)",
                "    except Erreur as e:", "        print(\"erreur\", e.message)",
                "    except Exception as e:", "        print(\"autre\", e.message)");
    }

    @Test
    void finallySemantics() throws Exception {
        same("def f(n: int) -> int:", "    try:", "        print(\"try\", n)", "        if n == 1:", "            return 10",
                "        if n == 2:", "            raise RuntimeError(\"x\")", "        return 20", "    except RuntimeError:", "        print(\"except\")",
                "        return 30", "    finally:", "        print(\"finally\", n)",
                "print(f(1), f(2), f(3))",
                "for i in range(3):", "    try:", "        if i == 1:", "            continue", "        if i == 2:", "            break", "        print(\"corps\", i)",
                "    finally:", "        print(\"nettoyage\", i)",
                "def g() -> int:", "    try:", "        try:", "            return 1", "        finally:", "            print(\"interne\")",
                "    finally:", "        print(\"externe\")", "print(g())",
                "try:", "    try:", "        raise RuntimeError(\"a\")", "    finally:", "        print(\"f1\")", "except RuntimeError as e:", "    print(\"capturé\", e.message)");
    }

    @Test
    void exceptionInsideFinallyAndNestedHandlers() throws Exception {
        same("def h():", "    try:", "        try:", "            raise ValueError(\"v\")", "        except RuntimeError:", "            print(\"pas ici\")",
                "    except ValueError as e:", "        print(\"ici\", e.message)",
                "        try:", "            raise RuntimeError(\"r\")", "        except RuntimeError as e2:", "            print(\"imbriqué\", e2.message)",
                "h()", "try:", "    try:", "        print(\"a\")", "    finally:", "        raise RuntimeError(\"depuis finally\")", "except RuntimeError as e:", "    print(e.message)");
    }

    @Test
    void uncaughtExceptionsExitWithCode1() throws Exception {
        Run r = same("print(\"avant\")", "raise ValueError(\"boom\")", "print(\"après\")");
        assertEquals(1, r.exit());
        assertEquals("avant\n", r.stdout());
        assertTrue(r.stderr().contains("Erreur non rattrapée") && r.stderr().contains("ValueError") && r.stderr().contains("boom"), r.stderr());
        Run base = same("raise Exception(\"générique\")");
        assertTrue(base.stderr().contains("Exception : générique") && !base.stderr().contains("DException"), base.stderr());
        Run custom = same("class MonErreur(Exception):", "    pass", "def main():", "    raise MonErreur(\"perso\")");
        assertTrue(custom.stderr().contains("MonErreur") && custom.stderr().contains("perso"), custom.stderr());
    }

    @Test
    void assertions() throws Exception {
        same("assert 1 == 1", "try:", "    assert 1 == 2, \"non\"", "except RuntimeError as e:", "    print(e.message)",
                "try:", "    assert false", "except RuntimeError as e:", "    print(e.message)");
    }

    @Test
    void withStatementClosesResources() throws Exception {
        same("class Ressource:", "    nom: str", "    def init(self, nom: str):", "        self.nom = nom", "        print(\"ouvre\", nom)",
                "    def close(self):", "        print(\"ferme\", self.nom)",
                "with Ressource(\"a\") as r:", "    print(\"dedans\", r.nom)",
                "try:", "    with Ressource(\"b\") as r2:", "        raise RuntimeError(\"x\")", "except RuntimeError:", "    print(\"capturé\")",
                "def f() -> int:", "    with Ressource(\"c\") as r3:", "        return 5", "print(f())");
    }

    // ---- programmes complets ------------------------------------------------------------------------------------------------------------

    @Test
    void scriptsAndModules() throws Exception {
        same("def double(n: int) -> int:", "    return n * 2", "total = 0", "for i in range(4):", "    total += double(i)", "print(total)");
        Map<String, String> m = new LinkedHashMap<>();
        m.put("app.outils", src("package app", "def _secret() -> int:", "    return 42", "def public() -> int:", "    return _secret() + 1",
                "class Compteur:", "    n: int = 0", "    def inc(self) -> int:", "        self.n += 1", "        return self.n"));
        m.put("main", src("import app.outils", "def main():", "    print(outils.public())",
                "    c = outils.Compteur()", "    c.inc()", "    print(c.inc())"));
        Run r = viaJvm(m, "main");
        assertEquals("43\n2\n", r.stdout());
        assertEquals(0, r.exit());
    }

    @Test
    void scriptVariablesAreSharedWithFunctionsMethodsAndLambdas() throws Exception {
        same("total = 0", "noms = [\"a\", \"b\"]", "ratio = 0.5", "actif = true", "limite: int? = null",
                "def ajouter(n: int):", "    total += n",
                "def lire() -> int:", "    return total",
                "def resume() -> str:", "    return f\"{total} {noms} {ratio} {actif} {limite}\"",
                "class Compte:", "    def solde(self) -> int:", "        return total * 2",
                "ajouter(5)", "ajouter(7)", "noms.add(\"c\")", "ratio = 1.5", "limite = 9",
                "print(lire(), Compte().solde(), resume())",
                "f = () -> total + 1", "total = 100", "print(f(), len(noms))",
                "for i in range(3):", "    ajouter(i)", "print(lire())");
    }

    @Test
    void genericOverridesGetBridgeMethods() throws Exception {
        same("class Boite[T]:", "    v: T", "    def init(self, v: T):", "        self.v = v",
                "    def get(self) -> T:", "        return self.v", "    def put(self, x: T):", "        self.v = x",
                "class Entier(Boite[int]):", "    def init(self, v: int):", "        super().init(v)",
                "    def get(self) -> int:", "        return self.v + 100", "    def put(self, x: int):", "        self.v = x * 2",
                "interface Source[T]:", "    def lire(self) -> T",
                "class Mots(Source[str]):", "    def lire(self) -> str:", "        return \"mot\"",
                "def main():", "    b: Boite[int] = Entier(1)", "    print(b.get())", "    b.put(5)", "    print(b.get())",
                "    s: Source[str] = Mots()", "    print(s.lire().upper())");
    }

    @Test
    void interfaceDefaultAndStaticMethods() throws Exception {
        same("interface Salut:", "    def nom(self) -> str",
                "    def dire(self) -> str:", "        return \"Bonjour \" + self.nom()",
                "    def deux(self, n: int) -> str:", "        return self.dire() + \" x\" + str(n)",
                "    @static", "    def defaut() -> str:", "        return \"anonyme\"",
                "interface Poli(Salut):", "    def formel(self) -> str:", "        return \"Monsieur \" + self.nom()",
                "class Ami(Salut):", "    def nom(self) -> str:", "        return \"Awa\"",
                "class Chef(Poli):", "    def nom(self) -> str:", "        return \"Diallo\"",
                "    def dire(self) -> str:", "        return \"Salutations \" + self.nom()",
                "class Cousin(Ami):", "    pass",
                "def main():", "    a = Ami()", "    c = Chef()",
                "    print(a.dire(), a.deux(2), c.dire(), c.deux(3), c.formel(), Cousin().dire())",
                "    s: Salut = c", "    print(s.dire(), Salut.defaut())");
    }

    @Test
    void uncaughtErrorsReportTheSameLineAsTheInterpreter() throws Exception {
        sameWithStderr("def f(n: int) -> float:", "    x = n + 1", "    return 10 / (x - 1)",
                "def main():", "    print(\"avant\")", "    print(f(3))", "    print(f(0))");
        sameWithStderr("class Erreur(Exception):", "    pass", "", "def boom():", "    raise Erreur(\"perso\")", "", "boom()");
        sameWithStderr("xs = [1, 2]", "", "print(\"début\")", "print(xs[7])");
        sameWithStderr("import concurrent", "def boom(n: int) -> int:", "    raise ValueError(\"dans la tâche\")",
                "def main():", "    t = concurrent.run(() -> boom(1))", "    print(\"lancé\")", "    t.await()");
        sameWithStderr("def main():", "    print(\"x\")", "    raise ValueError(\"\")");
    }

    @Test
    void unsupportedConstructsGiveE0401WithPosition() {
        for (String code : List.of(
                src("def main():", "    f = x -> x + 1"),
                src("def main():", "    print(filter(len, [\"a\"]))"),
                src("def main():", "    def g():", "        pass"))) {
            Compiler.Output o = Compiler.compile(code);
            Diagnostic d = o.diagnostics().stream().filter(Diagnostic::isError).findFirst().orElseThrow(() -> new AssertionError(code));
            assertTrue(d.code().equals("E0401") || d.code().startsWith("E0"), code + " -> " + d);
            assertTrue(o.classes().isEmpty(), code);
        }
    }

    // ---- limites du backend ------------------------------------------------------------------------------------

    @Test
    void infiniteRecursionExitsWithCode1() throws Exception {
        Run r = same("def f(n: int) -> int:", "    return f(n + 1)", "print(\"début\")", "f(0)");
        assertEquals(1, r.exit());
        assertTrue(r.stderr().contains("profondeur de récursion"), r.stderr());
    }

    @Test
    void methodsTooLargeForTheClassFormatAreReportedNotCrashed() {
        StringBuilder sb = new StringBuilder("def main():\n    x = 0\n");
        for (int i = 0; i < 12000; i++) {
            sb.append("    x += ").append(i + 100000).append('\n');
        }
        Compiler.Output o = Compiler.compile(sb.toString());
        assertTrue(!o.ok());
        Diagnostic d = o.diagnostics().stream().filter(Diagnostic::isError).findFirst().orElseThrow();
        assertEquals("E0401", d.code());
        assertTrue(d.message().contains("trop grande"), d.message());
        assertTrue(o.classes().isEmpty());
    }

    // ---- le .jar --------------------------------------------------------------------------------------------------------------------------------

    @Test
    void jarIsSelfContainedAndRunnable() throws Exception {
        Compiler.Output o = Compiler.compile(src("def main():", "    print(\"autonome\")"));
        Path jar = Files.createTempFile("dystos-jar-", ".jar");
        try {
            Compiler.writeJar(o, jar);
            try (JarFile jf = new JarFile(jar.toFile())) {
                assertEquals("main.$Module", jf.getManifest().getMainAttributes().getValue("Main-Class"));
                assertTrue(jf.getEntry("main/$Module.class") != null);
                assertTrue(jf.getEntry("dystos/rt/Rt.class") != null, "le runtime est embarqué");
            }
            Process p = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar.toString()).start();
            assertEquals("autonome\n", new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(0, p.waitFor());
        } finally {
            Files.deleteIfExists(jar);
        }
    }

    @Test
    void compilationIsDeterministic() throws IOException {
        String code = example("20");
        Compiler.Output a = Compiler.compile(code);
        Compiler.Output b = Compiler.compile(code);
        assertEquals(a.classes().keySet(), b.classes().keySet());
        for (String k : a.classes().keySet()) {
            assertTrue(java.util.Arrays.equals(a.classes().get(k), b.classes().get(k)), k);
        }
    }
}
