package com.vulsoft.dystos.interp;

import com.vulsoft.dystos.sema.Diagnostic;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lanceur en ligne de commande.
 *
 * <pre>
 * java -cp ... com.vulsoft.dystos.interp.Main run  programme.dys [options]
 * java -cp ... com.vulsoft.dystos.interp.Main test programme.dys
 * echo 'print(1)' | java -cp ... com.vulsoft.dystos.interp.Main run - --sandbox --json
 * </pre>
 *
 * Options : {@code --sandbox} (limites strictes du playground), {@code --json} (résultat JSON),
 * {@code --max-steps N}, {@code --timeout-ms N}, {@code --max-output N}.
 * Le fichier {@code -} lit le programme sur l'entrée standard. Sans {@code --sandbox}, le programme
 * s'exécute en mode « poste du développeur » (fichiers dans le dossier courant).
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        if (args.length < 2 || !(args[0].equals("run") || args[0].equals("test"))) {
            System.err.println("usage : Main (run|test) <fichier.dys | -> [--sandbox] [--json] "
                    + "[--max-steps N] [--timeout-ms N] [--max-output N]");
            System.exit(64);
        }
        boolean sandbox = false;
        boolean json = false;
        Long maxSteps = null;
        Long timeout = null;
        Long maxOutput = null;
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--sandbox": sandbox = true; break;
                case "--json": json = true; break;
                case "--max-steps": maxSteps = number(args, ++i); break;
                case "--timeout-ms": timeout = number(args, ++i); break;
                case "--max-output": maxOutput = number(args, ++i); break;
                default:
                    System.err.println("option inconnue : " + args[i]);
                    System.exit(64);
            }
        }
        String label = args[1].equals("-") ? "<stdin>" : args[1];
        String source = args[1].equals("-")
                ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8)
                : Files.readString(Path.of(args[1]), StandardCharsets.UTF_8);
        Runner.Options opts = sandbox ? Runner.Options.sandbox() : Runner.Options.trusted(Path.of("").toAbsolutePath());
        if (maxSteps != null) {
            opts = opts.withMaxSteps(maxSteps);
        }
        if (timeout != null) {
            opts = opts.withTimeout(timeout);
        }
        if (maxOutput != null) {
            opts = opts.withMaxOutput(maxOutput);
        }

        if (args[0].equals("test")) {
            Runner.TestReport rep = Runner.runTests(source, opts);
            for (Diagnostic d : rep.diagnostics()) {
                System.err.println(label + ":" + d.line() + ":" + d.column() + ": [" + d.code() + "] " + d.message());
            }
            System.out.print(rep.stdout());
            int ok = 0;
            for (Runner.TestResult r : rep.results()) {
                System.out.println((r.passed() ? "  réussi  " : "  ÉCHEC   ") + r.name()
                        + (r.passed() ? "" : " : " + r.message()));
                ok += r.passed() ? 1 : 0;
            }
            System.out.println(ok + " / " + rep.results().size() + " test(s) réussi(s)");
            System.exit(rep.allPassed() ? 0 : 1);
        }

        Runner.Result r = Runner.run(source, opts);
        if (json) {
            System.out.println(r.toJson());
        } else {
            for (Diagnostic d : r.diagnostics()) {
                System.err.println(label + ":" + d.line() + ":" + d.column() + ": [" + d.code() + "] " + d.message());
            }
            System.out.print(r.stdout());
            System.err.print(r.stderr());
        }
        System.exit(r.exitCode());
    }

    private static long number(String[] args, int i) {
        if (i >= args.length) {
            System.err.println("valeur manquante après " + args[i - 1]);
            System.exit(64);
        }
        try {
            return Long.parseLong(args[i]);
        } catch (NumberFormatException e) {
            System.err.println("nombre invalide : " + args[i]);
            System.exit(64);
            return 0;
        }
    }
}
