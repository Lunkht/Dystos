package dev.dystos.app.domain.usecase;

import dev.dystos.app.domain.repository.ProgressRepository;

/** Marque une leçon comme lue. */
public final class MarkLessonRead {

    private final ProgressRepository repository;

    public MarkLessonRead(ProgressRepository repository) {
        this.repository = repository;
    }

    public void execute(String lessonId) {
        repository.markLessonRead(lessonId);
    }
}