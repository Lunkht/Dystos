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
public final class ChapterListViewModel {

    private final GetChapter getChapter;
    private final GetProgress getProgress;
    private final ContentRepository contentRepository;

    public ChapterListViewModel(
            GetChapter getChapter,
            GetProgress getProgress,
            ContentRepository contentRepository) {
        this.getChapter = getChapter;
        this.getProgress = getProgress;
        this.contentRepository = contentRepository;
    }

    public State load() {
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
    public static final class ChapterItem {
        private final Chapter chapter;
        private final int readCount;
        private final int solvedCount;

        public ChapterItem(Chapter chapter, int readCount, int solvedCount) {
            this.chapter = chapter;
            this.readCount = readCount;
            this.solvedCount = solvedCount;
        }

        public String getId() {
            return chapter.getId();
        }

        public String getTitle() {
            return chapter.getTitle();
        }

        public int getLessonCount() {
            return chapter.getLessonCount();
        }

        public int getReadCount() {
            return readCount;
        }

        public int getSolvedCount() {
            return solvedCount;
        }

        public List<Lesson> getLessons() {
            return chapter.getLessons();
        }
    }

    /** État rendu par l'écran : chapitres, progression globale. */
    public static final class State {
        private final List<ChapterItem> items;
        private final int readLessons;
        private final int totalLessons;
        private final float ratio;

        public State(List<ChapterItem> items, int readLessons, int totalLessons, float ratio) {
            this.items = items;
            this.readLessons = readLessons;
            this.totalLessons = totalLessons;
            this.ratio = ratio;
        }

        public List<ChapterItem> getItems() {
            return items;
        }

        public int getReadLessons() {
            return readLessons;
        }

        public int getTotalLessons() {
            return totalLessons;
        }

        public float getRatio() {
            return ratio;
        }
    }
}