package dev.dystos.app.domain.usecase;

import java.util.List;

import dev.dystos.app.domain.model.Example;
import dev.dystos.app.domain.repository.ContentRepository;

/**
 * Catalogue des programmes d'exemple.
 *
 * Les exemples sont groupés par chapitre pour que l'utilisateur retrouve
 * rapidement ce qui l'intéresse.
 */
public final class GetExamples {

    private final ContentRepository repository;

    public GetExamples(ContentRepository repository) {
        this.repository = repository;
    }

    /** Tous les exemples, dans l'ordre du catalogue. */
    public List<Example> all() {
        return repository.getExamples();
    }

    /** Un groupe d'exemples, par chapitre. */
    public static final class Group {
        private final String category;
        private final List<Example> examples;

        Group(String category, List<Example> examples) {
            this.category = category;
            this.examples = examples;
        }

        public String getCategory() {
            return category;
        }

        public List<Example> getExamples() {
            return examples;
        }
    }

    /** Les exemples regroupés par chapitre, dans l'ordre du catalogue. */
    public List<Group> grouped() {
        java.util.LinkedHashMap<String, List<Example>> byCategory =
                new java.util.LinkedHashMap<>();

        for (Example example : all()) {
            List<Example> group = byCategory.get(example.getCategory());
            if (group == null) {
                group = new java.util.ArrayList<>();
                byCategory.put(example.getCategory(), group);
            }
            group.add(example);
        }

        List<Group> groups = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, List<Example>> entry : byCategory.entrySet()) {
            groups.add(new Group(entry.getKey(), List.copyOf(entry.getValue())));
        }
        return groups;
    }

    /** Un exemple par identifiant, ou null. */
    public Example one(String exampleId) {
        return repository.getExample(exampleId);
    }
}