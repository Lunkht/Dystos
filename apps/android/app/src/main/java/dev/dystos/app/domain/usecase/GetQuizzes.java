package dev.dystos.app.domain.usecase;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import dev.dystos.app.data.search.SearchAssetReader;
import dev.dystos.app.domain.model.Quiz;
import dev.dystos.app.domain.repository.ContentRepository;

/**
 * Catalogue des quiz.
 *
 * Les questions sont du contenu éditorial : elles vivent dans les assets, au
 * même endroit que les leçons. Seuls le bareme et la correction sont des
 * règles, et restent ici.
 */
public final class GetQuizzes {

    private final Context context;
    private final ContentRepository contentRepository;

    public GetQuizzes(Context context, ContentRepository contentRepository) {
        this.context = context.getApplicationContext();
        this.contentRepository = contentRepository;
    }

    /** Tous les quiz, dans l'ordre du catalogue. */
    public List<Quiz> all() {
        JSONArray array = SearchAssetReader.readArray(
                context, SearchAssetReader.QUIZZES, "quiz");
        List<Quiz> quizzes = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json != null) {
                Quiz quiz = parse(json);
                if (quiz != null && !quiz.isEmpty()) {
                    quizzes.add(quiz);
                }
            }
        }
        return quizzes;
    }

    /** Les quiz d'une leçon, pour proposer une révision à la fin d'un chapitre. */
    public List<Quiz> forLesson(String lessonId) {
        List<Quiz> matching = new ArrayList<>();
        for (Quiz quiz : all()) {
            if (quiz.getLessonId().equals(lessonId)) {
                matching.add(quiz);
            }
        }
        return matching;
    }

    /** Un quiz par identifiant, ou null. */
    public Quiz one(String quizId) {
        for (Quiz quiz : all()) {
            if (quiz.getId().equals(quizId)) {
                return quiz;
            }
        }
        return null;
    }

    /**
     * Le nombre de questions, pour afficher « 3 / 5 » pendant la révision.
     */
    public int questionCount(Quiz quiz) {
        return quiz == null ? 0 : quiz.getQuestions().size();
    }

    private Quiz parse(JSONObject json) {
        String id = text(json, "id", "");
        String lessonId = text(json, "lecon", "");
        if (id.isEmpty() || lessonId.isEmpty()) {
            return null;
        }

        List<Quiz.Question> questions = new ArrayList<>();
        JSONArray questionArray = json.optJSONArray("questions");
        if (questionArray != null) {
            for (int i = 0; i < questionArray.length(); i++) {
                JSONObject item = questionArray.optJSONObject(i);
                if (item != null) {
                    Quiz.Question question = parseQuestion(item);
                    if (question != null) {
                        questions.add(question);
                    }
                }
            }
        }

        return new Quiz(id, lessonId, text(json, "titre", id), questions);
    }

    private Quiz.Question parseQuestion(JSONObject json) {
        String prompt = text(json, "question", "");
        JSONArray choiceArray = json.optJSONArray("choix");
        if (prompt.isEmpty() || choiceArray == null || choiceArray.length() < 2) {
            return null;
        }

        List<String> choices = new ArrayList<>();
        for (int i = 0; i < choiceArray.length(); i++) {
            choices.add(choiceArray.optString(i, ""));
        }

        // La bonne réponse est désignée par son contenu : l'ordre des choix
        // peut être mélangé à l'affichage sans casser la correction.
        String expected = text(json, "reponse", "");
        int correctIndex = -1;
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).equals(expected)) {
                correctIndex = i;
                break;
            }
        }
        if (correctIndex < 0) {
            return null;
        }

        return new Quiz.Question(prompt, choices, correctIndex,
                text(json, "explication", ""));
    }

    private String text(JSONObject json, String key, String fallback) {
        if (!json.has(key) || json.isNull(key)) {
            return fallback;
        }
        String value = json.optString(key, fallback);
        return value == null ? fallback : value;
    }

    /** Vérifie que le quiz renvoie une leçon qui existe. */
    public boolean isLinkedToKnownLesson(Quiz quiz) {
        return quiz != null
                && contentRepository.getLesson(quiz.getLessonId()) != null;
    }
}