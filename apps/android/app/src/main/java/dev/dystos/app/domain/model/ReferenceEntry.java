package dev.dystos.app.domain.model;

import java.util.List;

/**
 * Une entrée de la référence rapide.
 *
 * La référence est regroupée par section (mots-clés, opérateurs, bibliothèque
 * standard) : c'est ce que l'on cherche quand on écrit un programme et qu'on
 * ne se souvient plus d'un nom.
 */
public final class ReferenceEntry {

    private final String name;
    private final String signature;
    private final String description;

    public ReferenceEntry(String name, String signature, String description) {
        this.name = name == null ? "" : name;
        this.signature = signature == null ? "" : signature;
        this.description = description == null ? "" : description;
    }

    public String getName() {
        return name;
    }

    /** Forme d'écriture, vide pour une section sans syntaxe particulière. */
    public String getSignature() {
        return signature;
    }

    public String getDescription() {
        return description;
    }

    /** Une section de la référence. */
    public static final class Section {
        private final String id;
        private final String title;
        private final String intro;
        private final List<ReferenceEntry> entries;

        public Section(String id, String title, String intro,
                       List<ReferenceEntry> entries) {
            this.id = id == null ? "" : id;
            this.title = title == null ? "" : title;
            this.intro = intro == null ? "" : intro;
            this.entries = entries == null ? List.of() : List.copyOf(entries);
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getIntro() {
            return intro;
        }

        public List<ReferenceEntry> getEntries() {
            return entries;
        }
    }
}