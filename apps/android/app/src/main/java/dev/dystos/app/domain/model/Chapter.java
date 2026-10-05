package dev.dystos.app.domain.model;

import java.util.Collections;
import java.util.List;

/** Un chapitre du parcours, contenant des leçons ordonnées. */
public final class Chapter {

    private final String id;
    private final String title;
    private final int order;
    private final List<Lesson> lessons;

    public Chapter(String id, String title, int order, List<Lesson> lessons) {
        this.id = id;
        this.title = title;
        this.order = order;
        this.lessons = lessons == null ? List.of() : List.copyOf(lessons);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getOrder() {
        return order;
    }

    /** Leçons du chapitre, dans l'ordre du parcours. */
    public List<Lesson> getLessons() {
        return Collections.unmodifiableList(lessons);
    }

    public int getLessonCount() {
        return lessons.size();
    }
}