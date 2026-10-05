package dev.dystos.app.domain.model;

/** Une leçon : un texte, un exemple, et éventuellement un exercice. */
public final class Lesson {

    private final String id;
    private final String chapterId;
    private final String title;
    private final String summary;
    private final int order;
    private final String exampleId;
    private final String body;
    private final Exercise exercise;

    public Lesson(
            String id,
            String chapterId,
            String title,
            String summary,
            int order,
            String exampleId,
            String body,
            Exercise exercise) {
        this.id = id;
        this.chapterId = chapterId;
        this.title = title;
        this.summary = summary == null ? "" : summary;
        this.order = order;
        this.exampleId = exampleId;
        this.body = body == null ? "" : body;
        this.exercise = exercise;
    }

    public String getId() {
        return id;
    }

    public String getChapterId() {
        return chapterId;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public int getOrder() {
        return order;
    }

    /** Identifiant de l'exemple associé, ou null. */
    public String getExampleId() {
        return exampleId;
    }

    public boolean hasExample() {
        return exampleId != null && !exampleId.isEmpty();
    }

    /** Corps de la leçon en Markdown. */
    public String getBody() {
        return body;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public boolean hasExercise() {
        return exercise != null;
    }
}