package dev.dystos.app.domain.model;

/**
 * Résultat de la vérification d'un exercice.
 *
 * Trois états possibles : une erreur de compilation, une sortie qui ne
 * correspond pas, ou une réussite. La distinction compte pour l'apprenant :
 * « ton code ne compile pas » et « ton code compile mais n'affiche pas la
 * bonne chose » ne se corrigent pas de la même façon.
 */
public final class CheckResult {

    /** Nature de l'échec, quand il y en a un. */
    public enum Kind {
        SUCCESS,
        COMPILE_ERROR,
        WRONG_OUTPUT,
        RUNTIME_ERROR,
        TIMEOUT
    }

    private final Kind kind;
    private final String message;
    private final String actualOutput;
    private final String expectedOutput;
    private final int line;

    private CheckResult(Kind kind, String message, String actualOutput,
                        String expectedOutput, int line) {
        this.kind = kind;
        this.message = message == null ? "" : message;
        this.actualOutput = actualOutput == null ? "" : actualOutput;
        this.expectedOutput = expectedOutput == null ? "" : expectedOutput;
        this.line = line;
    }

    public static CheckResult success(String actualOutput) {
        return new CheckResult(Kind.SUCCESS, "", actualOutput, "", 0);
    }

    public static CheckResult compileError(String message, int line) {
        return new CheckResult(Kind.COMPILE_ERROR, message, "", "", line);
    }

    public static CheckResult runtimeError(String message, int line) {
        return new CheckResult(Kind.RUNTIME_ERROR, message, "", "", line);
    }

    public static CheckResult wrongOutput(String actual, String expected) {
        return new CheckResult(Kind.WRONG_OUTPUT, "", actual, expected, 0);
    }

    public static CheckResult timeout(String message) {
        return new CheckResult(Kind.TIMEOUT, message, "", "", 0);
    }

    public Kind kind() {
        return kind;
    }

    public boolean isSuccess() {
        return kind == Kind.SUCCESS;
    }

    /** Message destiné à l'apprenant, vide en cas de succès. */
    public String message() {
        return message;
    }

    public String actualOutput() {
        return actualOutput;
    }

    public String expectedOutput() {
        return expectedOutput;
    }

    /** Ligne fautive, ou 0 si l'erreur n'est pas localisée. */
    public int line() {
        return line;
    }
}