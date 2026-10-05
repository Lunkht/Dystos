package dev.dystos.app.presentation.chapter;

import java.util.ArrayList;
import java.util.List;

import dev.dystos.app.domain.model.Chapter;
import dev.dystos.app.domain.model.Lesson;
import dev.dystos.app.domain.model.Progress;
import dev.dystos.app.domain.repository.ContentRepository;
import dev.dystos.app.domain.repository.ProgressRepository;
import dev.dystos.app.domain.usecase.GetChapter;
import dev.dystos.app.domain.usecase.GetProgress;

/** Prépare la liste des chapitres et la progression associée. */
final class ChapterListViewModel {

    private final GetChapter getChapter;
    private final GetProgress getProgress;
    private final ContentRepository contentRepository;

    ChapterListViewModel(
            GetChapter getChapter,
            GetProgress getProgress,
            ContentRepository contentRepository) {
        this.getChapter = getChapter;
        this.getProgress = getProgress;
        this.contentRepository = contentRepository;
    }

    State load() {
        List<Chapter> chapters = getChapter.execute();
        Progress progress = getProgress.execute();
        int totalLessons = contentRepository.countLessons();

        List<ChapterItem> items = new ArrayList<>(chapters.size());
        for (Chapter chapter : chapters) {
            int read = 0;
            int solved = 0;
            for (Lesson lesson : chapter.getLessons()) {
                if (progress.isLessonRead(lesson.getId())) {
                    read++;
                }
                if (lesson.hasExercise() && progress.isExerciseSolved(lesson.getId())) {
                    solved++;
                }
            }
            items.add(new ChapterItem(chapter, read, solved));
        }

        return new State(items, progress.readCount(totalLessons), totalLessons, progress.ratio(totalLessons));
    }

    /** Ligne de la liste : un chapitre et son avancement. */
    static final class ChapterItem {
        private final Chapter chapter;
        private final int readCount;
        private final int solvedCount;

        ChapterItem(Chapter chapter, int readCount, int solvedCount) {
            this.chapter = chapter;
            this.readCount = readCount;
            this.solvedCount = solvedCount;
        }

        String getId() {
            return chapter.getId();
        }

        String getTitle() {
            return chapter.getTitle();
        }

        int getLessonCount() {
            return chapter.getLessonCount();
        }

        int getReadCount() {
            return readCount;
        }

        int getSolvedCount() {
            return solvedCount;
        }

        List<Lesson> getLessons() {
            return chapter.getLessons();
        }
    }

    /** État rendu par l'écran : chapitres, progression globale. */
    static final class State {
        private final List<ChapterItem> items;
        private final int readLessons;
        private final int totalLessons;
        private final float ratio;

        State(List<ChapterItem> items, int readLessons, int totalLessons, float ratio) {
            this.items = items;
            this.readLessons = readLessons;
            this.totalLessons = totalLessons;
            this.ratio = ratio;
        }

        List<ChapterItem> getItems() {
            return items;
        }

        int getReadLessons() {
            return readLessons;
        }

        int getTotalLessons() {
            return totalLessons;
        }

        float getRatio() {
            return ratio;
        }
    }
}