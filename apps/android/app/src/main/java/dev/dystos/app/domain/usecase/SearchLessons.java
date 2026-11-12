package dev.dystos.app.domain.usecase;

import java.util.List;

import dev.dystos.app.data.search.SqliteSearchIndex;
import dev.dystos.app.domain.model.SearchHit;

/**
 * Recherche plein texte dans les leçons.
 *
 * Le domaine dépend d'un index concret parce que l'implémentation n'a qu'un seul
 * choix possible ici (SQLite FTS, embarqué, hors ligne). Si un jour une autre
 * source devenait pertinente, c'est cette classe qu'il faudrait remplacer.
 */
public final class SearchLessons {

    /** Nombre de résultats affichés au-delà duquel la liste n'aide plus. */
    private static final int DEFAULT_LIMIT = 20;

    private final SqliteSearchIndex index;

    public SearchLessons(SqliteSearchIndex index) {
        this.index = index;
    }

    /** Les leçons correspondant à la requête, les plus pertinentes d'abord. */
    public List<SearchHit> search(String query) {
        return search(query, DEFAULT_LIMIT);
    }

    public List<SearchHit> search(String query, int limit) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        return index.search(query, limit);
    }

    /** Des mots vers lesquels suggérer une recherche. */
    public List<String> suggestions() {
        return index.suggest(8);
    }
}