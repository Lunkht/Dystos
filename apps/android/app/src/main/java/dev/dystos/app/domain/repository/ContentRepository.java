package dev.dystos.app.domain.repository;

import java.util.List;

import dev.dystos.app.domain.model.Chapter;
import dev.dystos.app.domain.model.Lesson;

/**
 * Accès au contenu du parcours.
 *
 * L'implémentation lit le paquet d'assets généré par tools/build-content. Le
 * contenu est disponible sans réseau.
 */
public interface ContentRepository {

    /** Tous les chapitres, dans l'ordre du parcours. */
    List<Chapter> getChapters();

    /** Une leçon par identifiant, ou null si elle n'existe pas. */
    Lesson getLesson(String lessonId);

    /** Nombre total de leçons du parcours. */
    int countLessons();
}