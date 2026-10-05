package dev.dystos.app.domain.usecase;

import dev.dystos.app.domain.repository.ProgressRepository;

/** Marque l'exercice d'une leçon comme réussi. */
public final class MarkExerciseSolved {

    private final ProgressRepository repository;

    public MarkExerciseSolved(ProgressRepository repository) {
        this.repository = repository;
    }

    public void execute(String lessonId) {
        repository.markExerciseSolved(lessonId);
    }
}