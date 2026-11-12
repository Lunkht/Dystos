package dev.dystos.app.domain.gateway;

/**
 * Exécution d'un code Dystos.
 *
 * Cette interface est le point d'entrée du domaine vers le moteur
 * d'exécution. Le domaine ne connaît ni le lexer, ni l'interpréteur : il
 * demande seulement « exécute ceci et rends-moi la sortie ». Le moteur vit
 * dans la couche data et peut être remplacé ou rester hors ligne.
 */
public interface CodeRunner {

    /**
     * Nature d'un échec.
     *
     * La distinction compte pour l'apprenant : une faute de frappe se corrige
     * seule, une erreur d'exécution demande de relire la logique.
     */
    enum Failure {
        NONE,
        /** Le programme n'a pas pu être analysé. */
        COMPILE,
        /** Le programme a été analysé mais s'est arrêté en cours d'exécution. */
        RUNTIME,
        /** Le programme a dépassé son budget d'exécution. */
        TIMEOUT
    }

    /** Résultat d'une exécution, avec la sortie et l'erreur éventuelle. */
    final class RunResult {

        private final Failure failure;
        private final String output;
        private final String errorMessage;
        private final int errorLine;

        private RunResult(Failure failure, String output, String errorMessage,
                          int errorLine) {
            this.failure = failure;
            this.output = output == null ? "" : output;
            this.errorMessage = errorMessage;
            this.errorLine = errorLine;
        }

        public static RunResult ok(String output) {
            return new RunResult(Failure.NONE, output, null, 0);
        }

        public static RunResult compileError(String message, int line) {
            return new RunResult(Failure.COMPILE, "", message, line);
        }

        public static RunResult error(String message, int line) {
            return new RunResult(Failure.RUNTIME, "", message, line);
        }

        public static RunResult timeout(String message) {
            return new RunResult(Failure.TIMEOUT, "", message, 0);
        }

        public boolean isFailed() {
            return failure != Failure.NONE;
        }

        public Failure failure() {
            return failure;
        }

        public String output() {
            return output;
        }

        public String errorMessage() {
            return errorMessage == null ? "" : errorMessage;
        }

        public int errorLine() {
            return errorLine;
        }

        public boolean isCompileError() {
            return failure == Failure.COMPILE;
        }

        public boolean isTimedOut() {
            return failure == Failure.TIMEOUT;
        }
    }

    /**
     * Analyse puis exécute un programme.
     *
     * @param code programme Dystos complet
     * @return résultat de l'exécution, jamais nul
     */
    RunResult run(String code);
}