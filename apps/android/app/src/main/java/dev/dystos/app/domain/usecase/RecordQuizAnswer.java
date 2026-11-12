package dev.dystos.app.domain.usecase;

import java.util.Collections;
import java.util.Map;

/**
 * Mémorise le score d'un quiz.
 *
 * Le score est stocké à part de la progression des leçons : réussir un quiz et
 * lire une leçon sont deux choses distinctes, et l'application ne doit pas
 * déduire l'une de l'autre.
 */
public final class RecordQuizAnswer {

    private final QuizScoreStore store;

    public RecordQuizAnswer(QuizScoreStore store) {
        this.store = store;
    }

    /** Enregistre un résultat et renvoie le score sur 20. */
    public int record(String quizId, dev.dystos.app.domain.model.Quiz.Result result) {
        if (quizId == null || result == null) {
            return -1;
        }
        store.put(quizId, result.getScoreOutOf20());
        return result.getScoreOutOf20();
    }

    /** Le dernier score connu d'un quiz, ou -1 s'il n'a jamais été passé. */
    public int lastScore(String quizId) {
        if (quizId == null) {
            return -1;
        }
        Integer score = store.get(quizId);
        return score == null ? -1 : score;
    }

    public boolean hasAnswered(String quizId) {
        return lastScore(quizId) >= 0;
    }

    /**
     * Tous les scores connus.
     *
     * Sert à afficher « 18/20 » sur la liste des quiz sans refaire de lecture
     * par quiz.
     */
    public Map<String, Integer> all() {
        return Collections.unmodifiableMap(store.all());
    }

    /** Stockage des scores de quiz. */
    public interface QuizScoreStore {
        void put(String quizId, int score);

        Integer get(String quizId);

        Map<String, Integer> all();
    }
}