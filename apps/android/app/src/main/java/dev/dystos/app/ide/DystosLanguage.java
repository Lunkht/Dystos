package dev.dystos.app.ide;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import dev.dystos.app.data.code.DystosCodeRunner;
import dev.dystos.app.domain.gateway.CodeRunner;
import dev.dystos.app.domain.model.CheckResult;
import dev.dystos.app.language.CodeAnalysis;

/** Le langage de l'application : Dystos, analysé et exécuté localement. */
public final class DystosLanguage implements Language {

    private static final String DEFAULT_CODE =
            "val message: str = \"Bonjour, Dystos !\"\n"
                    + "print(message)\n\n"
                    + "def carre(n: int) -> int:\n"
                    + "    return n * n\n\n"
                    + "print(carre(5))\n";

    private static final List<String> SYMBOLS = Arrays.asList(
            ":", "(", ")", "[", "]", "{", "}", "->", "?", ".", ":", "\"", "_");

    private final CodeRunner runner = new DystosCodeRunner();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public String id() {
        return "dystos";
    }

    @Override
    public String displayName() {
        return "Dystos";
    }

    @Override
    public String fileExtension() {
        return "dys";
    }

    @Override
    public String defaultCode() {
        return DEFAULT_CODE;
    }

    @Override
    public List<String> symbols() {
        return SYMBOLS;
    }

    @Override
    public CodeAnalysis.Result analyse(String source) {
        return CodeAnalysis.analyse(source);
    }

    @Override
    public CheckResult run(String source) {
        CodeRunner.RunResult run = runner.run(source);
        switch (run.failure()) {
            case TIMEOUT:
                return CheckResult.timeout(run.errorMessage());
            case COMPILE:
                return CheckResult.compileError(run.errorMessage(), run.errorLine());
            case RUNTIME:
                return CheckResult.runtimeError(run.errorMessage(), run.errorLine());
            case NONE:
            default:
                return CheckResult.success(run.output());
        }
    }

    @Override
    public void runAsync(String source, Consumer<CheckResult> onResult) {
        executor.execute(() -> onResult.accept(run(source)));
    }

    @Override
    public String expand(String symbol, String textBeforeCursor) {
        if ((":".equals(symbol) || "->".equals(symbol))
                && endsWithKeyword(textBeforeCursor)) {
            return " " + symbol + " ";
        }
        if ("\"".equals(symbol)) {
            return "\"\"";
        }
        if ("_".equals(symbol)) {
            return "_";
        }
        return symbol;
    }

    /** Vrai si le texte jusque-là se termine par un mot-clé de déclaration. */
    private boolean endsWithKeyword(String before) {
        if (before == null || before.isEmpty()) {
            return false;
        }
        String window = before.length() > 12
                ? before.substring(before.length() - 12) : before;
        String trimmed = window.trim();
        return trimmed.endsWith("val") || trimmed.endsWith("var");
    }
}