package dev.dystos.app.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import dev.dystos.app.data.code.DystosCodeRunner;
import dev.dystos.app.domain.model.CheckResult;
import dev.dystos.app.domain.model.Exercise;

import java.util.List;

public class CheckExerciseTest {

    /** Dépôt de progression qui compte les réussites sans base de données. */
    private static final class FakeProgressRepository
            implements dev.dystos.app.domain.repository.ProgressRepository {

        private final java.util.Set<String> solved = new java.util.HashSet<>();

        @Override
        public dev.dystos.app.domain.model.Progress get() {
            java.util.Map<String, Boolean> read = new java.util.LinkedHashMap<>();
            java.util.Map<String, Boolean> exercises = new java.util.LinkedHashMap<>();
            for (String id : solved) {
                exercises.put(id, true);
            }
            return new dev.dystos.app.domain.model.Progress(read, exercises);
        }

        @Override
        public void markLessonRead(String lessonId) {
        }

        @Override
        public void markExerciseSolved(String lessonId) {
            solved.add(lessonId);
        }

        @Override
        public void reset() {
            solved.clear();
        }

        boolean isSolved(String lessonId) {
            return solved.contains(lessonId);
        }
    }

    private final FakeProgressRepository progress = new FakeProgressRepository();
    private final CheckExercise checkExercise =
            new CheckExercise(new DystosCodeRunner(), new MarkExerciseSolved(progress));

    private Exercise exercise(String expectedOutput, String initialCode) {
        return new Exercise("Consigne", initialCode, expectedOutput,
                List.of(), "solution");
    }

    @Test
    public void sortieConformeEstUnSucces() {
        CheckResult result = checkExercise.execute(
                exercise("Bonjour, Dystos !", ""),
                "print(\"Bonjour, Dystos !\")", "hello-world");

        assertTrue(result.isSuccess());
        assertEquals("Bonjour, Dystos !", result.actualOutput());
        assertTrue(progress.isSolved("hello-world"));
    }

    @Test
    public void sortieNonConformeEstSignalee() {
        CheckResult result = checkExercise.execute(
                exercise("Dystos 1.0", ""),
                "version: str = \"1.0\"\nnom: str = \"Dystos\"\n"
                        + "message: str = nom + version\nprint(message)", "variables");

        assertEquals(CheckResult.Kind.WRONG_OUTPUT, result.kind());
        // `nom + version` colle les deux chaînes sans espace.
        assertEquals("Dystos1.0", result.actualOutput());
        assertEquals("Dystos 1.0", result.expectedOutput());
        assertTrue("un échec ne doit pas être mémorisé", !progress.isSolved("variables"));
    }

    @Test
    public void solutionDeLexerciceEstAcceptee() {
        CheckResult result = checkExercise.execute(
                exercise("Dystos 1.0", ""),
                "version: str = \"1.0\"\nnom: str = \"Dystos\"\n"
                        + "message: str = f\"{nom} {version}\"\nprint(message)", "variables");

        assertTrue(result.isSuccess());
    }

    @Test
    public void espacesDeBordEtLigneFinaleSontTolerees() {
        CheckResult result = checkExercise.execute(
                exercise("Bonjour, Dystos !", ""),
                "print(\"Bonjour, Dystos !  \\n\\n\")", "lecon");

        assertTrue(result.isSuccess());
    }

    @Test
    public void sortieDifferentementCasseeEstRefusee() {
        CheckResult result = checkExercise.execute(
                exercise("Bonjour", ""), "print(\"bonjour\")", "lecon");

        assertEquals(CheckResult.Kind.WRONG_OUTPUT, result.kind());
    }

    @Test
    public void erreurDeSyntaxeEstDistincte() {
        CheckResult result = checkExercise.execute(
                exercise("x", ""), "print(", "lecon");

        assertEquals(CheckResult.Kind.COMPILE_ERROR, result.kind());
    }

    @Test
    public void codeVideEstRefuse() {
        CheckResult result = checkExercise.execute(exercise("x", ""), "   ", "lecon");

        assertEquals(CheckResult.Kind.COMPILE_ERROR, result.kind());
        assertTrue(result.message().contains(" programme"));
    }

    @Test
    public void exerciceSansSortieAttendueReussit_siLeCodeTourne() {
        Exercise open = new Exercise("Consigne", "", null, List.of(), "");

        CheckResult result = checkExercise.execute(open, "print(\"peu importe\")", "lecon");

        assertTrue(result.isSuccess());
        assertEquals("peu importe", result.actualOutput());
        assertTrue("un exercice libre n'est pas mémorisé", !progress.isSolved("lecon"));
    }

    @Test
    public void erreurDeSyntaxeNEstPasMemorisee() {
        checkExercise.execute(exercise("x", ""), "print(", "lecon");
        checkExercise.execute(exercise("x", ""), "print(1 / 0)", "lecon");

        assertTrue("aucun échec ne doit être mémorisé", !progress.isSolved("lecon"));
    }

    @Test
    public void exerciceEstResoluUneSeuleFois() {
        Exercise ex = exercise("ok", "");
        checkExercise.execute(ex, "print(\"ok\")", "lecon");
        CheckResult apres = checkExercise.execute(ex, "print(\"ok\")", "lecon");

        assertTrue(apres.isSuccess());
        assertTrue(progress.isSolved("lecon"));
    }

    @Test
    public void boucleInfinieEstArretee() {
        CheckResult result = checkExercise.execute(
                exercise("x", ""), "while true:\n    print(1)", "lecon");

        assertEquals(CheckResult.Kind.TIMEOUT, result.kind());
    }

    @Test
    public void divisionParZeroEstSignalee() {
        CheckResult result = checkExercise.execute(
                exercise("x", ""), "print(1 / 0)", "lecon");

        assertEquals(CheckResult.Kind.RUNTIME_ERROR, result.kind());
        assertTrue(result.message().contains("division"));
    }

    @Test
    public void typeNonDeclareCompatibleEstVerifie() {
        CheckResult result = checkExercise.execute(
                exercise("x", ""), "val n: int = \"texte\"", "lecon");

        assertEquals(CheckResult.Kind.RUNTIME_ERROR, result.kind());
        assertTrue(result.message().contains("int"));
    }

    @Test
    public void erreurDeSyntaxeIndiqueLaLigne() {
        CheckResult result = checkExercise.execute(
                exercise("x", ""), "val n: int = 1\nif n > 0\n    print(n)", "lecon");

        assertEquals(CheckResult.Kind.COMPILE_ERROR, result.kind());
        assertTrue("la ligne fautive doit être signalée, ligne=" + result.line(),
                result.line() >= 1);
    }

    @Test
    public void erreurDeTypeEstDistincteDeLaSyntaxe() {
        CheckResult result = checkExercise.execute(
                exercise("x", ""), "var n: int = 0\nn = \"texte\"\nprint(n)", "lecon");

        assertEquals(CheckResult.Kind.RUNTIME_ERROR, result.kind());
    }

    @Test
    public void exerciceSurLesFonctionsEstValide() {
        CheckResult result = checkExercise.execute(
                exercise("Bonjour, Awa !\nÀ bientôt\nBonjour, Moussa !\nÀ bientôt", ""),
                "def saluer(nom: str):\n"
                        + "    print(f\"Bonjour, {nom} !\")\n"
                        + "    print(\"À bientôt\")\n\n"
                        + "saluer(\"Awa\")\nsaluer(\"Moussa\")", "fonctions");

        assertTrue(result.isSuccess());
        assertTrue(progress.isSolved("fonctions"));
    }
}