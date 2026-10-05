package dev.dystos.app.domain.model;

import java.util.List;

/** L'exercice associé à une leçon, avec sa solution et ses indices. */
public final class Exercise {

    private final String instruction;
    private final String initialCode;
    private final String expectedOutput;
    private final List<String> hints;
    private final String solution;

    public Exercise(
            String instruction,
            String initialCode,
            String expectedOutput,
            List<String> hints,
            String solution) {
        this.instruction = instruction == null ? "" : instruction;
        this.initialCode = initialCode == null ? "" : initialCode;
        this.expectedOutput = expectedOutput;
        this.hints = hints == null ? List.of() : List.copyOf(hints);
        this.solution = solution == null ? "" : solution;
    }

    public String getInstruction() {
        return instruction;
    }

    /** Code de départ proposé à l'apprenant. */
    public String getInitialCode() {
        return initialCode;
    }

    /** Sortie attendue, ou null si l'exercice n'est pas vérifiable par sortie. */
    public String getExpectedOutput() {
        return expectedOutput;
    }

    public boolean isVerifiable() {
        return expectedOutput != null && !expectedOutput.isEmpty();
    }

    public List<String> getHints() {
        return hints;
    }

    public String getSolution() {
        return solution;
    }
}