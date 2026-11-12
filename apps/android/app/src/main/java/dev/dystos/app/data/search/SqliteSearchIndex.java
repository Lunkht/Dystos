package dev.dystos.app.data.search;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.dystos.app.domain.model.SearchHit;

/**
 * Index de recherche plein texte des leçons, en SQLite FTS.
 *
 * L'index est construit une fois au premier accès depuis les assets, puis
 * interrogeable sans réseau. On utilise FTS4 et non FTS5 : FTS4 est présent
 * sur toutes les versions d'Android supportées, alors que FTS5 dépend du
 * constructeur de la plateforme.
 */
public final class SqliteSearchIndex extends SQLiteOpenHelper {

    private static final String TAG = "DystosSearch";

    private static final String DATABASE_NAME = "recherche.db";
    private static final int DATABASE_VERSION = 2;
    private static final String TABLE = "documents";

    /**
     * Nom de la table FTS, cité dans les requêtes.
     *
     * Un nom ASCII évite d'avoir à vérifier comment chaque version de SQLite
     * traite un identifiant accentué dans une requête préparée.
     */
    private static final String QUOTED_TABLE = "\"" + TABLE + "\"";

    private final Context context;
    private volatile boolean ready;

    public SqliteSearchIndex(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // La table FTS stocke son propre contenu : `snippet()` et `ORDER BY
        // rank` en dépendent, et le corpus tient en quelques kilo-octets.
        db.execSQL("CREATE VIRTUAL TABLE " + QUOTED_TABLE
                + " USING fts4(id, titre, chapitre, resume, texte);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + QUOTED_TABLE + ";");
        onCreate(db);
    }

    /**
     * Remplit l'index depuis les assets si nécessaire.
     *
     * Le remplissage est idempotent : la table reste vide seulement si le
     * contenu n'a pas pu être lu, auquel cas la recherche renvoie simplement
     * zéro résultat plutôt que de planter.
     */
    private void ensureReady() {
        if (ready) {
            return;
        }
        synchronized (this) {
            if (ready) {
                return;
            }
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                int existing = countRows(db);
                if (existing == 0) {
                    populate(db);
                }
                db.setTransactionSuccessful();
                ready = true;
            } catch (RuntimeException e) {
                Log.e(TAG, "index de recherche indisponible", e);
            } finally {
                db.endTransaction();
            }
        }
    }

    private int countRows(SQLiteDatabase db) {
        android.database.Cursor cursor =
                db.rawQuery("SELECT COUNT(*) FROM " + QUOTED_TABLE, null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    private void populate(SQLiteDatabase db) {
        org.json.JSONArray documents = SearchAssetReader.readArray(
                context, SearchAssetReader.SEARCH_INDEX, "documents");
        for (int i = 0; i < documents.length(); i++) {
            org.json.JSONObject json = documents.optJSONObject(i);
            if (json == null) {
                continue;
            }
            db.execSQL("INSERT INTO " + QUOTED_TABLE
                            + "(id, titre, chapitre, resume, texte) VALUES (?, ?, ?, ?, ?);",
                    new Object[]{
                            optString(json, "id"),
                            optString(json, "titre"),
                            optString(json, "chapitre"),
                            optString(json, "resume"),
                            optString(json, "texte"),
                    });
        }
    }

    private String optString(org.json.JSONObject json, String key) {
        return json.has(key) && !json.isNull(key) ? json.optString(key, "") : "";
    }

    /**
     * Recherche les leçons dont un mot correspond à la requête.
     *
     * La requête est nettoyée avant MATCH : un utilisateur tape `def ?` ou
     * `"` sans vouloir une requête FTS invalide, et une recherche qui plante
     * sur une faute de frappe est inutile.
     *
     * Le tri se fait sur le titre plutôt que sur `rank` : cette colonne
     * n'existe que si la table FTS a été créée avec `matchinfo`, ce que le
     * SQLite d'Android n'expose pas de façon fiable.
     */
    public List<SearchHit> search(String query, int limit) {
        String cleaned = toMatchQuery(query);
        if (cleaned.isEmpty()) {
            return List.of();
        }

        ensureReady();

        List<SearchHit> hits = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        android.database.Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT id, titre, chapitre, resume, "
                            + "snippet(" + QUOTED_TABLE + ", 4, '<b>', '</b>', ' … ', 12) "
                            + "FROM " + QUOTED_TABLE + " WHERE " + QUOTED_TABLE + " MATCH ? "
                            + "ORDER BY titre LIMIT ?;",
                    new String[]{cleaned, String.valueOf(Math.max(1, limit))});
            while (cursor.moveToNext()) {
                hits.add(new SearchHit(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4)));
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "requête de recherche refusée : " + query, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return hits;
    }

    /** Suggestions de mots-clés, pour l'écran d'aide à la recherche. */
    public List<String> suggest(int limit) {
        ensureReady();
        List<String> words = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        android.database.Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT texte FROM " + QUOTED_TABLE + " LIMIT 20;", null);
            Set<String> unique = new LinkedHashSet<>();
            while (cursor.moveToNext()) {
                for (String word : cursor.getString(0).split("[^\\p{L}\\p{N}_]+")) {
                    if (word.length() > 3 && Character.isLetter(word.charAt(0))) {
                        unique.add(word.toLowerCase(java.util.Locale.FRANCE));
                    }
                }
                if (unique.size() >= limit * 3) {
                    break;
                }
            }
            words.addAll(unique);
        } catch (RuntimeException e) {
            Log.w(TAG, "suggestions indisponibles", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return words.subList(0, Math.min(words.size(), limit));
    }

    /**
     * Transforme une saisie en requête FTS valide.
     *
     * Chaque mot est entouré de guillemets doubles, ce qui neutralise la
     * syntaxe FTS et évite qu'un `*` ou un `-` tape par accident ne fasse
     * échouer la requête. Le préfixe `*` est conservé pour la recherche
     * progressive.
     */
    static String toMatchQuery(String query) {
        if (query == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String word : query.trim().split("\\s+")) {
            String cleaned = word.replaceAll("[^\\p{L}\\p{N}_]", "");
            if (cleaned.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append('"').append(cleaned.toLowerCase(java.util.Locale.FRANCE)).append('"');
        }
        return builder.toString();
    }

    /** Ferme l'index proprement. */
    public void closeQuietly() {
        try {
            close();
        } catch (RuntimeException e) {
            Log.w(TAG, "fermeture de l'index impossible", e);
        }
    }
}