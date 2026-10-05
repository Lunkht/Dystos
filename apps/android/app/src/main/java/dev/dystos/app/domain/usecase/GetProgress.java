package dev.dystos.app.domain.usecase;

import dev.dystos.app.domain.model.Progress;
import dev.dystos.app.domain.repository.ProgressRepository;

/** Récupère la progression locale. */
public final class GetProgress {

    private final ProgressRepository repository;

    public GetProgress(ProgressRepository repository) {
        this.repository = repository;
    }

    public Progress execute() {
        return repository.get();
    }
}