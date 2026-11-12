package dev.dystos.app.ide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les langages disponibles dans l'IDE.
 *
 * Le projet reste volontairement sans framework d'injection : un registre
 * statique suffit. Ajouter un langage revient à implémenter {@link Language}
 * et à l'enregistrer ici.
 */
public final class LanguageRegistry {

    private static final Map<String, Language> LANGUAGES = new LinkedHashMap<>();

    static {
        register(new DystosLanguage());
        register(new JavascriptLanguage());
    }

    private LanguageRegistry() {
    }

    public static void register(Language language) {
        LANGUAGES.put(language.id(), language);
    }

    /** Tous les langages, dans l'ordre d'enregistrement. */
    public static List<Language> all() {
        return Collections.unmodifiableList(new ArrayList<>(LANGUAGES.values()));
    }

    /** Premier langage enregistré, celui sélectionné par défaut. */
    public static Language defaultLanguage() {
        return LANGUAGES.values().iterator().next();
    }

    public static Language get(String id) {
        Language language = LANGUAGES.get(id);
        if (language == null) {
            throw new IllegalArgumentException("Langage inconnu : " + id);
        }
        return language;
    }

    public static boolean exists(String id) {
        return id != null && LANGUAGES.containsKey(id);
    }
}