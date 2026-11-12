package dev.dystos.app.domain.model;

import java.util.List;

/**
 * Un quiz de révision.
 *
 * Un quiz porte sur une leçon : il sert à vérifier qu'on a compris avant de
 * passer à la suite. Les questions sont à choix multiple, une seule réponse
 * étant correcte.
 */
public final class Quiz {

    private final String id;
    private final String lessonId;
    private final String title;
    private final List<Question> questions;

    public Quiz(String id, String lessonId, String title, List<Question> questions) {
        this.id = id == null ? "" : id;
        this.lessonId = lessonId == null ? "" : lessonId;
        this.title = title == null ? "" : title;
        this.questions = questions == null ? List.of() : List.copyOf(questions);
    }

    public String getId() {
        return id;
    }

    /** La leçon concernée, pour relier le score à la progression. */
    public String getLessonId() {
        return lessonId;
    }

    public String getTitle() {
        return title;
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public boolean isEmpty() {
        return questions.isEmpty();
    }

    /** Une question à choix multiple. */
    public static final class Question {
        private final String prompt;
        private final List<String> choices;
        private final int correctIndex;
        private final String explanation;

        public Question(String prompt, List<String> choices, int correctIndex,
                        String explanation) {
            this.prompt = prompt == null ? "" : prompt;
            this.choices = choices == null ? List.of() : List.copyOf(choices);
            this.correctIndex = correctIndex;
            this.explanation = explanation == null ? "" : explanation;
        }

        public String getPrompt() {
            return prompt;
        }

        public List<String> getChoices() {
            return choices;
        }

        /** Index de la bonne réponse parmi les choix. */
        public int getCorrectIndex() {
            return correctIndex;
        }

        /** Pourquoi cette réponse est la bonne. */
        public String getExplanation() {
            return explanation;
        }

        public boolean hasCorrectIndex() {
            return correctIndex >= 0 && correctIndex < choices.size();
        }
    }

    /** Réponse à une question, et correction. */
    public static final class Answer {
        private final boolean correct;
        private final String explanation;

        public Answer(boolean correct, String explanation) {
            this.correct = correct;
            this.explanation = explanation == null ? "" : explanation;
        }

        public boolean isCorrect() {
            return correct;
        }

        public String getExplanation() {
            return explanation;
        }
    }

    /** Résultat d'un quiz complet. */
    public static final class Result {
        private final int correct;
        private final int total;

        public Result(int correct, int total) {
            this.correct = correct;
            this.total = total;
        }

        public int getCorrect() {
            return correct;
        }

        public int getTotal() {
            return total;
        }

        /** Score sur 20, comme une note de cours. */
        public int getScoreOutOf20() {
            if (total <= 0) {
                return 0;
            }
            return Math.round(correct * 20f / total);
        }

        /** Une révision réussie : il faut plus de la moitié des bonnes réponses. */
        public boolean isPassed() {
            return total > 0 && correct * 2 > total;
        }
    }
}