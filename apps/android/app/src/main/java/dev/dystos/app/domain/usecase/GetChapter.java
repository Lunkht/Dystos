package dev.dystos.app.domain.usecase;

import java.util.List;

import dev.dystos.app.domain.model.Chapter;
import dev.dystos.app.domain.repository.ContentRepository;

/** Récupère les chapitres du parcours. */
public final class GetChapter {

    private final ContentRepository repository;

    public GetChapter(ContentRepository repository) {
        this.repository = repository;
    }

    public List<Chapter> execute() {
        return repository.getChapters();
    }
}