package dev.dystos.app.data.content;

import android.content.res.AssetManager;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import dev.dystos.app.core.json.Json;
import dev.dystos.app.domain.model.Chapter;
import dev.dystos.app.domain.model.Example;
import dev.dystos.app.domain.model.Exercise;
import dev.dystos.app.domain.model.Lesson;
import dev.dystos.app.domain.repository.ContentRepository;

/**
 * Lit le paquet d'assets généré par tools/build-content.
 *
 * Le contenu est lu une fois au premier accès et mis en cache : le parcours
 * reste utilisable en mode avion et les listes défilent sans coût.
 */
public final class ContentRepositoryImpl implements ContentRepository {

    private static final String TAG = "DystosContent";
    private static final String CONTENT_ASSET = "content/content.json";
    private static final String EXAMPLES_ASSET = "content/exemples.json";

    private final AssetManager assets;
    private volatile List<Chapter> chapters;
    private volatile List<Lesson> lessons;
    private volatile List<Example> examples;

    public ContentRepositoryImpl(AssetManager assets) {
        this.assets = assets;
    }

    @Override
    public List<Chapter> getChapters() {
        load();
        return chapters;
    }

    @Override
    public Lesson getLesson(String lessonId) {
        load();
        if (lessonId == null) {
            return null;
        }
        for (Lesson lesson : lessons) {
            if (lessonId.equals(lesson.getId())) {
                return lesson;
            }
        }
        return null;
    }

    @Override
    public int countLessons() {
        load();
        return lessons.size();
    }

    @Override
    public List<Example> getExamples() {
        loadExamples();
        return examples;
    }

    @Override
    public Example getExample(String exampleId) {
        loadExamples();
        if (exampleId == null) {
            return null;
        }
        for (Example example : examples) {
            if (exampleId.equals(example.getId())) {
                return example;
            }
        }
        return null;
    }

    private void load() {
        if (chapters != null) {
            return;
        }
        synchronized (this) {
            if (chapters != null) {
                return;
            }
            List<Chapter> parsedChapters = new ArrayList<>();
            List<Lesson> parsedLessons = new ArrayList<>();
            try {
                JSONObject root = Json.object(readAsset(CONTENT_ASSET));
                JSONArray chapterArray = Json.optArray(root, "chapitres");
                for (int i = 0; i < chapterArray.length(); i++) {
                    JSONObject chapterJson = Json.optObject(chapterArray, i);
                    if (chapterJson == null) {
                        Log.w(TAG, "chapitre ignore : entrée " + i + " malformée");
                        continue;
                    }
                    parsedChapters.add(parseChapter(chapterJson, parsedLessons));
                }
                parsedChapters.sort(Comparator.comparingInt(Chapter::getOrder));
            } catch (IOException e) {
                Log.e(TAG, "impossible de lire " + CONTENT_ASSET, e);
            }
            chapters = List.copyOf(parsedChapters);
            lessons = List.copyOf(parsedLessons);
        }
    }

    /**
     * Charge le catalogue d'exemples.
     *
     * Volontairement séparé du parcours : ouvrir un exemple n'a pas à passer
     * par les chapitres.
     */
    private void loadExamples() {
        if (examples != null) {
            return;
        }
        synchronized (this) {
            if (examples != null) {
                return;
            }
            List<Example> parsed = new ArrayList<>();
            try {
                JSONObject root = Json.object(readAsset(EXAMPLES_ASSET));
                JSONArray array = Json.optArray(root, "exemples");
                for (int i = 0; i < array.length(); i++) {
                    JSONObject json = Json.optObject(array, i);
                    if (json == null) {
                        Log.w(TAG, "exemple ignoré : entrée " + i + " malformée");
                        continue;
                    }
                    parsed.add(parseExample(json));
                }
                parsed.sort(Comparator.comparingInt(Example::getOrder));
            } catch (IOException e) {
                Log.e(TAG, "impossible de lire " + EXAMPLES_ASSET, e);
            }
            examples = List.copyOf(parsed);
        }
    }

    private Example parseExample(JSONObject json) {
        return new Example(
                Json.optString(json, "id", ""),
                Json.optString(json, "titre", ""),
                Json.optString(json, "chapitre", ""),
                Json.optString(json, "categorie", null),
                Json.optInt(json, "ordre", 0),
                Json.optString(json, "code", ""),
                Json.optString(json, "sortieAttendue", null),
                Json.optString(json, "note", ""));
    }

    private Chapter parseChapter(JSONObject json, List<Lesson> accumulator) {
        String id = Json.optString(json, "id", "");
        String title = Json.optString(json, "titre", id);
        int order = Json.optInt(json, "ordre", 0);

        List<Lesson> chapterLessons = new ArrayList<>();
        JSONArray lessonArray = Json.optArray(json, "lessons");
        for (int i = 0; i < lessonArray.length(); i++) {
            JSONObject lessonJson = Json.optObject(lessonArray, i);
            if (lessonJson == null) {
                Log.w(TAG, "leçon ignorée dans " + id + " : entrée " + i + " malformée");
                continue;
            }
            Lesson lesson = parseLesson(id, lessonJson);
            chapterLessons.add(lesson);
            accumulator.add(lesson);
        }
        chapterLessons.sort(Comparator.comparingInt(Lesson::getOrder));

        return new Chapter(id, title, order, chapterLessons);
    }

    private Lesson parseLesson(String chapterId, JSONObject json) {
        return new Lesson(
                Json.optString(json, "id", ""),
                chapterId,
                Json.optString(json, "titre", ""),
                Json.optString(json, "resume", ""),
                Json.optInt(json, "ordre", 0),
                Json.optString(json, "exemple", null),
                Json.optString(json, "corps", ""),
                parseExercise(Json.optObject(json, "exercice")));
    }

    private Exercise parseExercise(JSONObject json) {
        if (json == null) {
            return null;
        }
        return new Exercise(
                Json.optString(json, "consigne", ""),
                Json.optString(json, "codeInitial", ""),
                Json.optString(json, "sortieAttendue", null),
                Json.toStringList(Json.optArray(json, "indices")),
                Json.optString(json, "solution", ""));
    }

    private String readAsset(String path) throws IOException {
        return AssetReader.read(assets.open(path));
    }
}