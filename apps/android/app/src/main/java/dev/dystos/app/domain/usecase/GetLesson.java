package dev.dystos.app.domain.usecase;

import dev.dystos.app.domain.model.Lesson;
import dev.dystos.app.domain.repository.ContentRepository;

/** Récupère une leçon par identifiant. */
public final class GetLesson {

    private final ContentRepository repository;

    public GetLesson(ContentRepository repository) {
        this.repository = repository;
    }

    public Lesson execute(String lessonId) {
        return repository.getLesson(lessonId);
    }
}