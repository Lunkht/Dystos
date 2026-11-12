package dev.dystos.app.domain.usecase;

import dev.dystos.app.domain.gateway.CodeRunner;
import dev.dystos.app.domain.model.CheckResult;
import dev.dystos.app.domain.model.Exercise;
import dev.dystos.app.domain.model.OutputComparator;

/**
 * Vérifie le code d'un apprenant contre la sortie attendue d'un exercice.
 *
 * Le domaine passe par l'interface {@link CodeRunner} : il ne connaît pas le
 * moteur d'exécution, seulement son résultat. La vérification reste entièrement
 * locale, sans appel réseau.
 *
 * Un exercice sans sortie attendue est réussi dès que le code s'exécute : le
 * même écran sert alors pour un exercice à retour libre.
 */
public final class CheckExercise {

    private final CodeRunner codeRunner;
    private final MarkExerciseSolved markExerciseSolved;

    public CheckExercise(CodeRunner codeRunner, MarkExerciseSolved markExerciseSolved) {
        this.codeRunner = codeRunner;
        this.markExerciseSolved = markExerciseSolved;
    }

    /**
     * Exécute {@code code} et compare sa sortie à celle de l'exercice.
     *
     * @param exercise exercice de référence, ou null s'il n'y en a pas
     * @param code     code écrit par l'apprenant
     * @param lessonId identifiant de la leçon, pour mémoriser la réussite
     */
    public CheckResult execute(Exercise exercise, String code, String lessonId) {
        CheckResult result = check(exercise, code);
        if (result.isSuccess() && exercise != null && exercise.isVerifiable()
                && lessonId != null) {
            markExerciseSolved.execute(lessonId);
        }
        return result;
    }

    /** Vérifie sans mémoriser la progression. */
    public CheckResult check(Exercise exercise, String code) {
        CodeRunner.RunResult run = codeRunner.run(code);
        if (run.isFailed()) {
            switch (run.failure()) {
                case TIMEOUT:
                    return CheckResult.timeout(run.errorMessage());
                case COMPILE:
                    return CheckResult.compileError(run.errorMessage(), run.errorLine());
                case NONE:
                case RUNTIME:
                default:
                    return CheckResult.runtimeError(run.errorMessage(), run.errorLine());
            }
        }

        if (exercise == null || !exercise.isVerifiable()) {
            return CheckResult.success(run.output());
        }

        String expected = exercise.getExpectedOutput();
        if (OutputComparator.matches(run.output(), expected)) {
            return CheckResult.success(run.output());
        }
        return CheckResult.wrongOutput(run.output(), expected);
    }
}