package dev.dystos.app.data.code;

import dev.dystos.app.domain.gateway.CodeRunner;
import dev.dystos.app.language.DystosException;
import dev.dystos.app.language.Interpreter;
import dev.dystos.app.language.Parser;

/**
 * Exécuteur de code Dystos.
 *
 * L'adaptation entre le domaine et le moteur local tient en une classe : le
 * domaine demande une exécution, cette classe analyse et exécute, puis traduit
 * les exceptions du moteur en résultats exploitables.
 */
public final class DystosCodeRunner implements CodeRunner {

    private static final long STEP_LIMIT = 200_000L;
    private static final int OUTPUT_LIMIT = 1_000;
    private static final String TIMEOUT_MESSAGE =
            "Le programme ne se termine pas : boucle infinie ?";

    @Override
    public RunResult run(String code) {
        if (code == null || code.trim().isEmpty()) {
            return RunResult.compileError("Écris un programme avant de vérifier.", 0);
        }

        try {
            Parser.parse(code);
        } catch (DystosException e) {
            return RunResult.compileError(e.getMessage(), e.getLine());
        }

        try {
            return RunResult.ok(Interpreter.run(code, STEP_LIMIT, OUTPUT_LIMIT));
        } catch (DystosException e) {
            if (isTimeout(e)) {
                return RunResult.timeout(TIMEOUT_MESSAGE);
            }
            return RunResult.error(e.getMessage(), e.getLine());
        } catch (StackOverflowError e) {
            return RunResult.error("Récursion trop profonde.", 0);
        }
    }

    /** Le moteur signale le dépassement de budget par ce message. */
    private boolean isTimeout(DystosException e) {
        return e.getMessage() != null && e.getMessage().contains("boucle infinie");
    }
}