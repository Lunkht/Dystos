package dev.dystos.app.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Le modèle de quiz, indépendamment du stockage.
 *
 * La correction se fait par index, mais elle ne doit être acceptée que si cet
 * index est valide : sinon un choix mal formé rend le quiz injouable sans
 * explication.
 */
public class QuizTest {

    @Test
    public void quizVideEstReconnu() {
        assertTrue(new Quiz("q", "lecon", "Titre", List.of()).isEmpty());
        assertFalse(new Quiz("q", "lecon", "Titre",
                List.of(question("a", "b"))).isEmpty());
    }

    @Test
    public void questionAccepteUnIndexValide() {
        Quiz.Question question = question("première", "seconde");

        assertTrue(question.hasCorrectIndex());
        assertEquals(0, question.getCorrectIndex());
        assertEquals(2, question.getChoices().size());
    }

    @Test
    public void questionRejetteUnIndexHorsDesChoix() {
        Quiz.Question horsBornes = new Quiz.Question(
                "énoncé", List.of("a", "b"), 5, "");

        assertFalse(horsBornes.hasCorrectIndex());
    }

    @Test
    public void scoreSurVingtEstArrondi() {
        assertEquals(20, new Quiz.Result(4, 4).getScoreOutOf20());
        assertEquals(15, new Quiz.Result(3, 4).getScoreOutOf20());
        assertEquals(5, new Quiz.Result(1, 4).getScoreOutOf20());
        assertEquals(0, new Quiz.Result(0, 4).getScoreOutOf20());
    }

    @Test
    public void scoreVautZeroSansQuestion() {
        // Diviser par zéro donnerait NaN : on le traite comme un zéro.
        assertEquals(0, new Quiz.Result(0, 0).getScoreOutOf20());
        assertFalse(new Quiz.Result(0, 0).isPassed());
    }

    @Test
    public void reussiteAMajorite() {
        assertTrue(new Quiz.Result(3, 4).isPassed());
        assertFalse(new Quiz.Result(2, 4).isPassed());
    }

    @Test
    public void reponseIndiqueSiElleEstJuste() {
        assertTrue(new Quiz.Answer(true, "car").isCorrect());
        assertFalse(new Quiz.Answer(false, "car").isCorrect());
        assertEquals("car", new Quiz.Answer(true, "car").getExplanation());
    }

    @Test
    public void champsNulsDeviennentDesChainesVides() {
        Quiz quiz = new Quiz(null, null, null, null);
        Quiz.Question question = new Quiz.Question(null, null, 0, null);

        assertEquals("", quiz.getId());
        assertEquals("", quiz.getLessonId());
        assertEquals("", quiz.getTitle());
        assertEquals("", question.getPrompt());
        assertEquals("", question.getExplanation());
        assertTrue(question.getChoices().isEmpty());
    }

    private Quiz.Question question(String... choices) {
        return new Quiz.Question("énoncé", List.of(choices), 0, "");
    }
}