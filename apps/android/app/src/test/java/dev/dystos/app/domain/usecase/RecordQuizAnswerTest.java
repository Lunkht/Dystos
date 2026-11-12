package dev.dystos.app.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;

import dev.dystos.app.domain.model.Quiz;

/**
 * La mémorisation des scores de quiz.
 *
 * Le score est stocké sur 20, et un quiz jamais passé vaut -1 : c'est ce qui
 * permet d'afficher « pas encore fait » plutôt que « 0 ».
 */
public class RecordQuizAnswerTest {

    @Test
    public void scoreInconnuVautMoinsUn() {
        RecordQuizAnswer useCase = new RecordQuizAnswer(new FakeStore());

        assertEquals(-1, useCase.lastScore("quiz-1"));
        assertFalse(useCase.hasAnswered("quiz-1"));
    }

    @Test
    public void enregistreLeScoreSurVingt() {
        FakeStore store = new FakeStore();
        RecordQuizAnswer useCase = new RecordQuizAnswer(store);

        int stored = useCase.record("quiz-1", new Quiz.Result(3, 4));

        assertEquals(15, stored);
        assertEquals(15, useCase.lastScore("quiz-1"));
        assertTrue(useCase.hasAnswered("quiz-1"));
    }

    @Test
    public void recommencerRemplaceLeScore() {
        FakeStore store = new FakeStore();
        RecordQuizAnswer useCase = new RecordQuizAnswer(store);

        useCase.record("quiz-1", new Quiz.Result(2, 4));
        useCase.record("quiz-1", new Quiz.Result(4, 4));

        assertEquals(20, useCase.lastScore("quiz-1"));
        assertEquals(1, store.all().size());
    }

    @Test
    public void quizOuResultatAbsentEstIgnore() {
        RecordQuizAnswer useCase = new RecordQuizAnswer(new FakeStore());

        assertEquals(-1, useCase.record(null, new Quiz.Result(1, 1)));
        assertEquals(-1, useCase.record("quiz-1", null));
        assertEquals(-1, useCase.lastScore(null));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void scoresEnLectureSeule() {
        new RecordQuizAnswer(new FakeStore()).all().put("quiz-1", 20);
    }

    @Test
    public void tousLesScoresSontExposes() {
        FakeStore store = new FakeStore();
        store.put("quiz-1", 15);
        store.put("quiz-2", 20);

        Map<String, Integer> scores = new RecordQuizAnswer(store).all();

        assertEquals(2, scores.size());
        assertEquals(Integer.valueOf(20), scores.get("quiz-2"));
    }

    /** Stockage en mémoire. */
    private static final class FakeStore implements RecordQuizAnswer.QuizScoreStore {

        private final Map<String, Integer> scores = new java.util.HashMap<>();

        @Override
        public void put(String quizId, int score) {
            scores.put(quizId, score);
        }

        @Override
        public Integer get(String quizId) {
            return scores.get(quizId);
        }

        @Override
        public Map<String, Integer> all() {
            return scores;
        }
    }
}