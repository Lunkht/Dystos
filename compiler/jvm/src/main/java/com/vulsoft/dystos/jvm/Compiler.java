package com.vulsoft.dystos.jvm;

import com.vulsoft.dystos.ast.Ast.SourceFile;
import com.vulsoft.dystos.lexer.LexerException;
import com.vulsoft.dystos.parser.ParseException;
import com.vulsoft.dystos.parser.Parser;
import com.vulsoft.dystos.sema.Diagnostic;
import com.vulsoft.dystos.sema.SemanticAnalyzer;
import com.vulsoft.dystos.sema.TypedProgram;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * Compilateur Dystos vers la JVM : analyse (lexer, parser, sémantique) puis génération de bytecode.
 *
 * <pre>
 * Compiler.Output o = Compiler.compile(source);
 * if (o.ok()) Compiler.writeJar(o, Path.of("programme.jar"));   // java -jar programme.jar
 * </pre>
 *
 * <p>Paliers de support : voir {@code README.md} du module. Une construction pas encore supportée donne le
 * diagnostic {@code E0401} avec sa position, jamais un bytecode incorrect.
 */
public final class Compiler {

    private Compiler() {
    }

    /**
     * @param classes   nom interne de classe → octets du fichier {@code .class} (vide si la compilation a échoué)
     * @param mainClass classe contenant {@code main(String[])} (nom avec des points)
     */
    public record Output(Map<String, byte[]> classes, String mainClass, List<Diagnostic> diagnostics) {

        public boolean ok() {
            return diagnostics.stream().noneMatch(Diagnostic::isError);
        }
    }

    public static Output compile(String source) {
        return compile(Map.of("main", source), "main");
    }

    /** @param sources module → texte ; @param entry module contenant {@code main} ou le script */
    public static Output compile(Map<String, String> sources, String entry) {
        List<SemanticAnalyzer.SourceUnit> units = new ArrayList<>();
        List<Diagnostic> diags = new ArrayList<>();
        boolean multi = sources.size() > 1;
        for (Map.Entry<String, String> en : sources.entrySet()) {
            try {
                SourceFile f = Parser.parse(en.getValue());
                units.add(new SemanticAnalyzer.SourceUnit(en.getKey(), f));
            } catch (LexerException e) {
                diags.add(parseDiag("E0001", e.getMessage(), e.line(), e.column(), multi ? en.getKey() : null));
            } catch (ParseException e) {
                diags.add(parseDiag("E0002", e.getMessage(), e.line(), e.column(), multi ? en.getKey() : null));
            }
        }
        if (!diags.isEmpty()) {
            return new Output(Map.of(), null, diags);
        }
        if (!sources.containsKey(entry)) {
            diags.add(new Diagnostic(Diagnostic.Severity.ERROR, "E0402", "module d'entrée introuvable : " + entry, 0, 0, null));
            return new Output(Map.of(), null, diags);
        }
        SemanticAnalyzer.Result r = SemanticAnalyzer.analyze(units);
        diags.addAll(r.diagnostics());
        if (r.hasErrors()) {
            return new Output(Map.of(), null, diags);
        }
        TypedProgram typed = r.typed();
        Map<String, byte[]> classes = new LinkedHashMap<>();
        List<ModuleGen.Problem> problems = new ArrayList<>();
        for (SemanticAnalyzer.SourceUnit u : typed.units) {
            new ModuleGen(typed, u.module(), u.file(), classes, problems).generate(u.module().equals(entry));
        }
        for (ModuleGen.Problem p : problems) {
            var pos = p.error().pos();
            diags.add(new Diagnostic(Diagnostic.Severity.ERROR, "E0401",
                    "non supporté par le backend JVM : " + p.error().getMessage(),
                    pos == null ? 0 : pos.line(), pos == null ? 0 : pos.column(), multi ? p.module() : null));
        }
        if (!problems.isEmpty()) {
            return new Output(Map.of(), null, diags);
        }
        return new Output(classes, Jt.moduleClass(entry).replace('/', '.'), diags);
    }

    private static Diagnostic parseDiag(String code, String message, int line, int col, String file) {
        int i = message.indexOf(" : ");
        return new Diagnostic(Diagnostic.Severity.ERROR, code, i >= 0 ? message.substring(i + 3) : message, line, col, file);
    }

    // ------------------------------------------------------------------
    // Archive .jar autonome
    // ------------------------------------------------------------------

    /** Écrit un {@code .jar} exécutable avec {@code java -jar} : classes générées + bibliothèque d'exécution. */
    public static void writeJar(Output o, Path jar) throws IOException {
        if (!o.ok()) {
            throw new IllegalArgumentException("la compilation a échoué : pas de .jar à écrire");
        }
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().put(Attributes.Name.MAIN_CLASS, o.mainClass());
        try (JarOutputStream jo = new JarOutputStream(Files.newOutputStream(jar), mf)) {
            for (Map.Entry<String, byte[]> en : o.classes().entrySet()) {
                put(jo, en.getKey() + ".class", en.getValue());
            }
            for (Map.Entry<String, byte[]> en : runtimeClasses().entrySet()) {
                put(jo, en.getKey(), en.getValue());
            }
        }
    }

    private static void put(JarOutputStream jo, String name, byte[] bytes) throws IOException {
        jo.putNextEntry(new JarEntry(name));
        jo.write(bytes);
        jo.closeEntry();
    }

    /** Classes de {@code dystos.rt} : entrée de jar → octets. */
    static Map<String, byte[]> runtimeClasses() throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try {
            Class<?> rt = Class.forName("dystos.rt.Rt");
            Path loc = Path.of(rt.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(loc)) {
                Path pkg = loc.resolve("dystos").resolve("rt");
                try (Stream<Path> s = Files.list(pkg)) {
                    for (Path p : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".class")).sorted()::iterator) {
                        out.put("dystos/rt/" + p.getFileName(), Files.readAllBytes(p));
                    }
                }
            } else {
                try (JarFile jf = new JarFile(loc.toFile())) {
                    for (JarEntry e : (Iterable<JarEntry>) jf.stream().filter(x -> x.getName().startsWith("dystos/rt/")
                            && x.getName().endsWith(".class"))::iterator) {
                        try (InputStream in = jf.getInputStream(e)) {
                            out.put(e.getName(), in.readAllBytes());
                        }
                    }
                }
            }
        } catch (ClassNotFoundException | URISyntaxException e) {
            throw new IOException("bibliothèque d'exécution dystos.rt introuvable", e);
        }
        if (out.isEmpty()) {
            throw new IOException("bibliothèque d'exécution dystos.rt vide");
        }
        return out;
    }
}
