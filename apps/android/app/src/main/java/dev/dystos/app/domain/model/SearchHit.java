package dev.dystos.app.domain.model;

/**
 * Un résultat de recherche dans les leçons.
 *
 * Le texte du snippet contient des balises `<b>` pour marquer la correspondance ;
 * c'est le seul fragment de donnée qui contient du balisage.
 */
public final class SearchHit {

    private final String lessonId;
    private final String title;
    private final String chapter;
    private final String summary;
    private final String snippet;

    public SearchHit(String lessonId, String title, String chapter,
                     String summary, String snippet) {
        this.lessonId = lessonId == null ? "" : lessonId;
        this.title = title == null ? "" : title;
        this.chapter = chapter == null ? "" : chapter;
        this.summary = summary == null ? "" : summary;
        this.snippet = snippet == null ? "" : snippet;
    }

    public String getLessonId() {
        return lessonId;
    }

    public String getTitle() {
        return title;
    }

    public String getChapter() {
        return chapter;
    }

    public String getSummary() {
        return summary;
    }

    /** Extrait du texte autour du mot trouvé, avec les Balises `<b>`. */
    public String getSnippet() {
        return snippet;
    }

    /** Le snippet est-il porteur d'information, ou seulement le résumé ? */
    public boolean hasSnippet() {
        return !snippet.trim().isEmpty();
    }
}