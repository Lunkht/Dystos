package com.vulsoft.dystos.interp;

import com.vulsoft.dystos.ast.Ast.Decorator;
import com.vulsoft.dystos.ast.Ast.FuncDecl;
import com.vulsoft.dystos.ast.Ast.SourceFile;
import com.vulsoft.dystos.ast.Ast.TopLevel;
import com.vulsoft.dystos.lexer.LexerException;
import com.vulsoft.dystos.parser.ParseException;
import com.vulsoft.dystos.parser.Parser;
import com.vulsoft.dystos.sema.Diagnostic;
import com.vulsoft.dystos.sema.SemanticAnalyzer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exécution de programmes Dystos : analyse (lexer, parser, sémantique) puis interprétation.
 *
 * <pre>
 * Runner.Result r = Runner.run(source, Runner.Options.sandbox());
 * System.out.print(r.stdout());
 * </pre>
 *
 * <p><b>Sécurité.</b> Le mode {@link Options#sandbox()} borne les instructions, la durée, la profondeur
 * et la sortie, désactive les fichiers et limite les classes Java accessibles. Ce n'est pas une
 * isolation complète : un service public doit en plus exécuter chaque programme dans un conteneur
 * éphémère sans réseau, avec limites de mémoire et de CPU.
 */
public final class Runner {

    private Runner() {
    }

    private static final List<String> SAFE_JAVA = List.of("java.util.", "java.time.", "java.math.", "java.text.",
            "java.lang.Math", "java.lang.StringBuilder", "java.lang.Integer", "java.lang.Long",
            "java.lang.Double", "java.lang.Character", "java.lang.Boolean");

    private static final List<String> DENIED_JAVA = List.of("java.util.concurrent", "java.util.logging",
            "java.util.zip", "java.util.jar", "java.util.prefs", "java.util.spi", "java.util.ServiceLoader",
            "java.util.Timer", "java.util.Scanner", "java.util.Properties", "java.util.ResourceBundle",
            "java.util.Formatter", "java.util.Random", "java.text.Bidi");

    /**
     * @param maxSteps       nombre maximal d'instructions et d'appels
     * @param maxOutputChars taille maximale de la sortie standard
     * @param maxDepth       profondeur maximale d'appels
     * @param timeoutMillis  durée maximale
     * @param allowFiles     autorise le module io (dans {@code workDir} uniquement)
     */
    public record Options(long maxSteps, long maxOutputChars, int maxDepth, long timeoutMillis,
                          boolean allowFiles, Path workDir, List<String> javaPrefixes, List<String> javaDenied) {

        /** Limites strictes : 20 M d'instructions, 64 000 caractères, 5 s, aucun fichier, Java restreint. */
        public static Options sandbox() {
            return new Options(20_000_000L, 64_000L, 800, 5_000L, false, null, SAFE_JAVA, DENIED_JAVA);
        }

        /** Mode « poste du développeur » : limites larges, fichiers dans {@code workDir}, tout {@code java.*}. */
        public static Options trusted(Path workDir) {
            return new Options(2_000_000_000L, 50_000_000L, 2_000, 600_000L, true, workDir,
                    List.of("java.", "javax."), List.of());
        }

        public Options withFiles(Path dir) {
            return new Options(maxSteps, maxOutputChars, maxDepth, timeoutMillis, true, dir, javaPrefixes, javaDenied);
        }

        public Options withTimeout(long millis) {
            return new Options(maxSteps, maxOutputChars, maxDepth, millis, allowFiles, workDir, javaPrefixes, javaDenied);
        }

        public Options withMaxSteps(long steps) {
            return new Options(steps, maxOutputChars, maxDepth, timeoutMillis, allowFiles, workDir, javaPrefixes, javaDenied);
        }

        public Options withMaxOutput(long chars) {
            return new Options(maxSteps, chars, maxDepth, timeoutMillis, allowFiles, workDir, javaPrefixes, javaDenied);
        }
    }

    /**
     * Résultat d'une exécution.
     * Codes de sortie : 0 succès ; 1 exception non rattrapée ; 2 erreurs de compilation ;
     * 3 limite dépassée (instructions, durée, sortie) ; 4 erreur interne.
     */
    public record Result(String stdout, String stderr, int exitCode, long durationMillis, List<Diagnostic> diagnostics) {

        public String toJson() {
            return "{\"stdout\":\"" + esc(stdout) + "\",\"stderr\":\"" + esc(stderr) + "\",\"exitCode\":" + exitCode
                    + ",\"durationMs\":" + durationMillis + ",\"diagnostics\":" + Diagnostic.toJson(diagnostics) + "}";
        }
    }

    public record TestResult(String name, boolean passed, String message) {
    }

    public record TestReport(List<TestResult> results, List<Diagnostic> diagnostics, String stdout) {

        public boolean allPassed() {
            return results.stream().allMatch(TestResult::passed) && diagnostics.stream().noneMatch(Diagnostic::isError);
        }
    }

    // ------------------------------------------------------------------
    // Exécution
    // ------------------------------------------------------------------

    public static Result run(String source, Options o) {
        return runModules(Map.of("main", source), "main", o);
    }

    /** @param sources module -> texte (ordre conservé) ; @param entry module contenant {@code main} ou le script */
    public static Result runModules(Map<String, String> sources, String entry, Options o) {
        long t0 = System.nanoTime();
        Parsed p = parse(sources);
        if (!p.diagnostics.isEmpty()) {
            return new Result("", "", 2, ms(t0), p.diagnostics);
        }
        SemanticAnalyzer.Result sema = SemanticAnalyzer.analyze(p.units, o.javaPrefixes(), o.javaDenied());
        if (sema.hasErrors()) {
            return new Result("", "", 2, ms(t0), sema.diagnostics());
        }
        if (!sources.containsKey(entry)) {
            return new Result("", "module d'entrée introuvable : " + entry, 4, ms(t0), sema.diagnostics());
        }
        Interpreter in = new Interpreter(o);
        Result[] box = new Result[1];
        Thread t = new Thread(null, () -> box[0] = execute(in, p, entry, t0, sema.diagnostics()),
                "dystos-run", 256L * 1024 * 1024);
        t.setDaemon(true);
        t.start();
        try {
            t.join(o.timeoutMillis() + 3_000L);
        } catch (InterruptedException e) {
            t.interrupt();
            Thread.currentThread().interrupt();
        }
        if (t.isAlive()) {
            t.interrupt();
            return new Result("", "Exécution interrompue : délai dépassé\n", 3, ms(t0), sema.diagnostics());
        }
        if (box[0] == null) {
            return new Result(in.out.toString(), "Erreur interne : l'exécution s'est arrêtée brutalement\n", 4, ms(t0),
                    sema.diagnostics());
        }
        return box[0];
    }

    private static Result execute(Interpreter in, Parsed p, String entry, long t0, List<Diagnostic> warnings) {
        try {
            in.load(toUnits(p));
            Interpreter.ModuleRt m = in.modules.get(entry);
            in.runModule(m);
            in.callMain(m);
            return new Result(in.out.toString(), "", 0, ms(t0), warnings);
        } catch (Interpreter.LimitExceeded e) {
            return new Result(in.out.toString(), "Exécution interrompue : " + e.getMessage() + "\n", 3, ms(t0), warnings);
        } catch (OutOfMemoryError e) {
            return new Result("", "Exécution interrompue : mémoire insuffisante\n", 3, ms(t0), warnings);
        } catch (RuntimeException | StackOverflowError e) {
            Interpreter.DystosThrow t = in.asThrow(e);
            if (t != null) {
                return new Result(in.out.toString(), describe(t), 1, ms(t0), warnings);
            }
            return new Result(in.out.toString(), "Erreur interne : " + e + "\n", 4, ms(t0), warnings);
        }
    }

    private static String describe(Interpreter.DystosThrow t) {
        Object msg = t.value.fields.get("message");
        return "Erreur non rattrapée (ligne " + t.line + ") : " + t.value.cls.name
                + (msg == null || msg.toString().isEmpty() ? "" : " : " + msg) + "\n";
    }

    // ------------------------------------------------------------------
    // Tests (@test)
    // ------------------------------------------------------------------

    public static TestReport runTests(String source, Options o) {
        Parsed p = parse(Map.of("main", source));
        if (!p.diagnostics.isEmpty()) {
            return new TestReport(List.of(), p.diagnostics, "");
        }
        SemanticAnalyzer.Result sema = SemanticAnalyzer.analyze(p.units, o.javaPrefixes(), o.javaDenied());
        if (sema.hasErrors()) {
            return new TestReport(List.of(), sema.diagnostics(), "");
        }
        Interpreter in = new Interpreter(o);
        List<TestResult> results = new ArrayList<>();
        Thread t = new Thread(null, () -> {
            try {
                in.load(toUnits(p));
                for (Interpreter.ModuleRt m : in.modules.values()) {
                    in.runModule(m);
                    for (TopLevel d : m.file.decls()) {
                        if (d instanceof FuncDecl f && f.decorators().stream().map(Decorator::name).anyMatch("test"::equals)) {
                            results.add(runOne(in, m, f));
                        }
                    }
                }
            } catch (RuntimeException e) {
                results.add(new TestResult("(chargement)", false, String.valueOf(e.getMessage())));
            }
        }, "dystos-test", 256L * 1024 * 1024);
        t.setDaemon(true);
        t.start();
        try {
            t.join(o.timeoutMillis() + 3_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new TestReport(results, sema.diagnostics(), in.out.toString());
    }

    private static TestResult runOne(Interpreter in, Interpreter.ModuleRt m, FuncDecl f) {
        try {
            in.callFunc((Values.FuncValue) m.env.vars.get(f.name()).value, null, new ArrayList<>(), Map.of());
            return new TestResult(f.name(), true, "");
        } catch (Interpreter.LimitExceeded e) {
            return new TestResult(f.name(), false, e.getMessage());
        } catch (RuntimeException | StackOverflowError e) {
            Interpreter.DystosThrow t = in.asThrow(e);
            return new TestResult(f.name(), false, t == null ? String.valueOf(e) : describe(t).strip());
        }
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private record Parsed(List<SemanticAnalyzer.SourceUnit> units, List<Diagnostic> diagnostics) {
    }

    private static Parsed parse(Map<String, String> sources) {
        List<SemanticAnalyzer.SourceUnit> units = new ArrayList<>();
        List<Diagnostic> diags = new ArrayList<>();
        boolean multi = sources.size() > 1;
        for (Map.Entry<String, String> en : sources.entrySet()) {
            try {
                units.add(new SemanticAnalyzer.SourceUnit(en.getKey(), Parser.parse(en.getValue())));
            } catch (LexerException e) {
                diags.add(diag("E0001", e.getMessage(), e.line(), e.column(), multi ? en.getKey() : null));
            } catch (ParseException e) {
                diags.add(diag("E0002", e.getMessage(), e.line(), e.column(), multi ? en.getKey() : null));
            }
        }
        return new Parsed(units, diags);
    }

    private static List<Interpreter.SourceFileUnit> toUnits(Parsed p) {
        List<Interpreter.SourceFileUnit> out = new ArrayList<>();
        for (SemanticAnalyzer.SourceUnit u : p.units) {
            SourceFile f = u.file();
            out.add(new Interpreter.SourceFileUnit(u.module(), f));
        }
        return out;
    }

    private static Diagnostic diag(String code, String message, int line, int col, String file) {
        int i = message.indexOf(" : ");
        return new Diagnostic(Diagnostic.Severity.ERROR, code, i >= 0 ? message.substring(i + 3) : message, line, col, file);
    }

    private static long ms(long t0) {
        return (System.nanoTime() - t0) / 1_000_000L;
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
