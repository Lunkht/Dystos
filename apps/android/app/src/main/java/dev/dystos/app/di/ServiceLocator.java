package dev.dystos.app.di;

import android.content.Context;

import dev.dystos.app.data.code.DystosCodeRunner;
import dev.dystos.app.data.content.ContentRepositoryImpl;
import dev.dystos.app.data.progress.ProgressRepositoryImpl;
import dev.dystos.app.data.search.SqliteQuizScoreStore;
import dev.dystos.app.data.search.SqliteSearchIndex;
import dev.dystos.app.domain.gateway.CodeRunner;
import dev.dystos.app.domain.repository.ContentRepository;
import dev.dystos.app.domain.repository.ProgressRepository;
import dev.dystos.app.domain.usecase.CheckExercise;
import dev.dystos.app.domain.usecase.GetChapter;
import dev.dystos.app.domain.usecase.GetExamples;
import dev.dystos.app.domain.usecase.GetLesson;
import dev.dystos.app.domain.usecase.GetProgress;
import dev.dystos.app.domain.usecase.GetQuizzes;
import dev.dystos.app.domain.usecase.GetReference;
import dev.dystos.app.domain.usecase.MarkExerciseSolved;
import dev.dystos.app.domain.usecase.MarkLessonRead;
import dev.dystos.app.domain.usecase.RecordQuizAnswer;
import dev.dystos.app.domain.usecase.SearchLessons;

/**
 * Localisation des dépendances.
 *
 * Le projet reste volontairement sans framework d'injection : les
 * dépendances sont peu nombreuses, toutes locales, et un conteneur explicite
 * évite une dépendance supplémentaire.
 */
public final class ServiceLocator {

    private static volatile ContentRepository contentRepository;
    private static volatile ProgressRepository progressRepository;
    private static volatile Context appContext;
    private static volatile RecordQuizAnswer.QuizScoreStore quizScoreStore;
    private static volatile SqliteSearchIndex searchIndex;

    private ServiceLocator() {
    }

    public static void initialize(Context context) {
        Context app = context.getApplicationContext();
        appContext = app;
        contentRepository = new ContentRepositoryImpl(app.getAssets());
        progressRepository = new ProgressRepositoryImpl(app);
        quizScoreStore = new SqliteQuizScoreStore(app);
    }

    public static ContentRepository contentRepository() {
        return require(contentRepository, "ServiceLocator non initialise");
    }

    public static ProgressRepository progressRepository() {
        return require(progressRepository, "ServiceLocator non initialise");
    }

    public static GetChapter getChapter() {
        return new GetChapter(contentRepository());
    }

    public static GetLesson getLesson() {
        return new GetLesson(contentRepository());
    }

    public static GetProgress getProgress() {
        return new GetProgress(progressRepository());
    }

    public static MarkLessonRead markLessonRead() {
        return new MarkLessonRead(progressRepository());
    }

    public static MarkExerciseSolved markExerciseSolved() {
        return new MarkExerciseSolved(progressRepository());
    }

    public static CodeRunner codeRunner() {
        return new DystosCodeRunner();
    }

    /** Contexte applicatif, pour les moteurs qui en ont besoin (ex. WebView). */
    public static Context appContext() {
        return context();
    }

    public static GetExamples getExamples() {
        return new GetExamples(contentRepository());
    }

    public static SearchLessons searchLessons() {
        return new SearchLessons(searchIndex());
    }

    public static GetReference getReference() {
        return new GetReference();
    }

    public static GetQuizzes getQuizzes() {
        return new GetQuizzes(context(), contentRepository());
    }

    public static RecordQuizAnswer recordQuizAnswer() {
        if (quizScoreStore == null) {
            throw new IllegalStateException("ServiceLocator non initialise");
        }
        return new RecordQuizAnswer(quizScoreStore);
    }

    /** Le contexte applicatif, nécessaire aux dépôts qui ouvrent des fichiers. */
    private static Context context() {
        if (appContext == null) {
            throw new IllegalStateException("ServiceLocator non initialise");
        }
        return appContext;
    }

    /**
     * L'index de recherche est unique : il tient une connexion SQLite ouverte,
     * en créer un par appel viderait le cache de l'index à chaque frappe.
     */
    private static SqliteSearchIndex searchIndex() {
        if (searchIndex == null) {
            searchIndex = new SqliteSearchIndex(context());
        }
        return searchIndex;
    }

    public static CheckExercise checkExercise() {
        return new CheckExercise(codeRunner(), markExerciseSolved());
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }
}