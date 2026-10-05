package dev.dystos.app.di;

import android.content.Context;

import dev.dystos.app.data.content.ContentRepositoryImpl;
import dev.dystos.app.data.progress.ProgressRepositoryImpl;
import dev.dystos.app.domain.repository.ContentRepository;
import dev.dystos.app.domain.repository.ProgressRepository;
import dev.dystos.app.domain.usecase.GetChapter;
import dev.dystos.app.domain.usecase.GetLesson;
import dev.dystos.app.domain.usecase.GetProgress;
import dev.dystos.app.domain.usecase.MarkLessonRead;

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

    private ServiceLocator() {
    }

    public static void initialize(Context context) {
        Context appContext = context.getApplicationContext();
        contentRepository = new ContentRepositoryImpl(appContext.getAssets());
        progressRepository = new ProgressRepositoryImpl(appContext);
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

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }
}