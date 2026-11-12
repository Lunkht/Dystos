package dev.dystos.app.domain.repository;

import java.util.List;

import dev.dystos.app.domain.model.Chapter;
import dev.dystos.app.domain.model.Example;
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

    /**
     * Les programmes d'exemple, dans l'ordre du catalogue.
     *
     * Les exemples ne sont pas des leçons : ils n'ont ni exercice ni
     * progression, et s'ouvrent directement dans l'éditeur.
     */
    List<Example> getExamples();

    /** Un exemple par identifiant, ou null s'il n'existe pas. */
    Example getExample(String exampleId);
}