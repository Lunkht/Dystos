package dev.dystos.app.domain.repository;

import dev.dystos.app.domain.model.Progress;

/**
 * Stockage de la progression de l'apprenant.
 *
 * L'implémentation est locale. Une synchronisation vers un serveur
 * auto-hébergé est une évolution possible, jamais une dépendance.
 */
public interface ProgressRepository {

    Progress get();

    void markLessonRead(String lessonId);

    void markExerciseSolved(String lessonId);

    void reset();
}