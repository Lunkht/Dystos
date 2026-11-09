package com.vulsoft.dystos.jvm;

import com.vulsoft.dystos.sema.Diagnostic;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lanceur du compilateur JVM.
 *
 * <pre>
 * build programme.dys [-o programme.jar]
 * build --module app.outils=outils.dys --module main=main.dys --entry main -o prog.jar
 * run   programme.dys            (compile dans un fichier temporaire puis l'exécute avec java -jar)
 * </pre>
 * Codes de sortie : 0 succès ; 2 erreurs de compilation ou construction non supportée ; 64 usage.
 */
public final class BuildMain {

    private BuildMain() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        if (args.length < 2 || !(args[0].equals("build") || args[0].equals("run"))) {
            System.err.println("usage : (build|run) <fichier.dys> [-o sortie.jar]\n"
                    + "        build --module NOM=FICHIER ... --entry NOM [-o sortie.jar]");
            System.exit(64);
        }
        boolean run = args[0].equals("run");
        Map<String, String> sources = new LinkedHashMap<>();
        String entry = "main";
        String single = null;
        Path jar = null;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "-o": jar = Path.of(args[++i]); break;
                case "--entry": entry = args[++i]; break;
                case "--module": {
                    String[] kv = args[++i].split("=", 2);
                    sources.put(kv[0], Files.readString(Path.of(kv[1]), StandardCharsets.UTF_8));
                    break;
                }
                default:
                    single = args[i];
            }
        }
        String label = "<programme>";
        if (single != null) {
            sources.put("main", Files.readString(Path.of(single), StandardCharsets.UTF_8));
            label = single;
        }
        Compiler.Output o = Compiler.compile(sources, entry);
        for (Diagnostic d : o.diagnostics()) {
            System.err.println(label + ":" + d.line() + ":" + d.column() + ": [" + d.code() + "] " + d.message());
        }
        if (!o.ok()) {
            System.exit(2);
        }
        if (jar == null) {
            jar = run ? Files.createTempFile("dystos-", ".jar")
                    : Path.of(single != null ? single.replaceAll("\\.dys$", "") + ".jar" : "programme.jar");
        }
        Compiler.writeJar(o, jar);
        if (!run) {
            System.out.println("OK : " + jar);
            return;
        }
        Process p = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dfile.encoding=UTF-8", "-jar", jar.toString()).inheritIO().start();
        int code = p.waitFor();
        Files.deleteIfExists(jar);
        System.exit(code);
    }
}
