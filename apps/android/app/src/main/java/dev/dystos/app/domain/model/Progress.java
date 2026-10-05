package dev.dystos.app.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Progression de l'apprenant, conservée localement.
 *
 * Les leçons lues et les exercices réussis sont mémorisés par identifiant :
 * le contenu est versionné avec l'application, les identifiants sont donc
 * stables.
 */
public final class Progress {

    private final Map<String, Boolean> lessonsRead;
    private final Map<String, Boolean> exercisesSolved;

    public Progress(Map<String, Boolean> lessonsRead, Map<String, Boolean> exercisesSolved) {
        this.lessonsRead = copyOf(lessonsRead);
        this.exercisesSolved = copyOf(exercisesSolved);
    }

    private static Map<String, Boolean> copyOf(Map<String, Boolean> source) {
        return source == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(source);
    }

    public boolean isLessonRead(String lessonId) {
        return Boolean.TRUE.equals(lessonsRead.get(lessonId));
    }

    public boolean isExerciseSolved(String lessonId) {
        return Boolean.TRUE.equals(exercisesSolved.get(lessonId));
    }

    public Map<String, Boolean> getLessonsRead() {
        return Collections.unmodifiableMap(lessonsRead);
    }

    public Map<String, Boolean> getExercisesSolved() {
        return Collections.unmodifiableMap(exercisesSolved);
    }

    /** Nombre de leçons lues sur le total fourni. */
    public int readCount(int total) {
        return Math.min(count(lessonsRead), total);
    }

    /** Progression globale entre 0 et 1. */
    public float ratio(int total) {
        if (total <= 0) {
            return 0f;
        }
        return Math.min(1f, count(lessonsRead) / (float) total);
    }

    private static int count(Map<String, Boolean> map) {
        int total = 0;
        for (Boolean value : map.values()) {
            if (Boolean.TRUE.equals(value)) {
                total++;
            }
        }
        return total;
    }
}