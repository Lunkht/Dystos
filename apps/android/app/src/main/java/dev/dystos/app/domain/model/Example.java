package dev.dystos.app.domain.model;

/**
 * Un programme d'exemple.
 *
 * Les exemples sont hors parcours : ils servent de référence et s'ouvrent
 * directement dans l'éditeur. Leur code est généré depuis la même source que le
 * contenu du site, donc le même programme est affiché partout.
 *
 * Un exemple porte soit une sortie attendue, soit une note expliquant ce que le
 * moteur embarqué ne sait pas encore faire. Jamais les deux, jamais aucune : un
 * exemple qui n'expliquerait rien laisserait l'utilisateur devant un échec
 * incompréhensible.
 */
public final class Example {

    private final String id;
    private final String title;
    private final String chapter;
    private final String category;
    private final int order;
    private final String code;
    private final String expectedOutput;
    private final String note;

    public Example(String id, String title, String chapter, String category,
                   int order, String code, String expectedOutput) {
        this(id, title, chapter, category, order, code, expectedOutput, "");
    }

    public Example(String id, String title, String chapter, String category,
                   int order, String code, String expectedOutput, String note) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.chapter = chapter == null ? "" : chapter;
        // Une catégorie absente ou vide retombe sur le chapitre : l'utilisateur
        // voit toujours un intitulé, jamais une ligne sans titre.
        this.category = category == null || category.trim().isEmpty()
                ? this.chapter : category;
        this.order = order;
        this.code = code == null ? "" : code;
        this.expectedOutput = expectedOutput;
        this.note = note == null ? "" : note;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    /** Identifiant du chapitre, pour regrouper dans la liste. */
    public String getChapter() {
        return chapter;
    }

    /** Libellé lisible du chapitre, pour l'affichage. */
    public String getCategory() {
        return category;
    }

    public int getOrder() {
        return order;
    }

    public String getCode() {
        return code;
    }

    /** Sortie attendue, ou null si l'exemple n'est pas vérifiable. */
    public String getExpectedOutput() {
        return expectedOutput;
    }

    public boolean isVerifiable() {
        return expectedOutput != null && !expectedOutput.trim().isEmpty();
    }

    /**
     * Ce que le moteur ne sait pas encore exécuter, ou une chaîne vide.
     *
     * Un exemple non exécutable reste affiché : il documente une étape du
     * langage. L'interface doit simplement le dire au lieu de laisser
     * l'utilisateur échouer sur une fonctionnalité absente.
     */
    public String getNote() {
        return note;
    }

    /** Vrai si le moteur ne sait pas encore exécuter cet exemple. */
    public boolean isNotExecutableYet() {
        return !isVerifiable() && !note.isEmpty();
    }

    /** L'exemple se présente comme un exercice, pour réutiliser l'éditeur. */
    public Exercise toExercise() {
        return new Exercise(title, code, expectedOutput,
                java.util.List.of(), "");
    }
}