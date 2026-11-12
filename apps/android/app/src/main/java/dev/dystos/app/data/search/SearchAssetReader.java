package dev.dystos.app.data.search;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Lecture des assets JSON du contenu.
 *
 * Isolé pour que le chargement de fichier ne soit dupliqué entre l'index de
 * recherche et le catalogue de quiz.
 */
public final class SearchAssetReader {

    /** Index des leçons pour la recherche. */
    public static final String SEARCH_INDEX = "content/search-index.json";

    /** Catalogue des quiz de révision. */
    public static final String QUIZZES = "content/quiz.json";

    private static final String TAG = "DystosAssets";

    private SearchAssetReader() {
    }

    /** Lit un objet JSON d'un asset, ou null si l'asset est illisible. */
    public static JSONObject readObject(Context context, String path) {
        String source = read(context, path);
        if (source == null) {
            return null;
        }
        try {
            return new JSONObject(source);
        } catch (org.json.JSONException e) {
            Log.e(TAG, "JSON invalide dans " + path, e);
            return null;
        }
    }

    /** Lit un tableau contenu dans un objet JSON d'un asset, ou un tableau vide. */
    public static JSONArray readArray(Context context, String path, String field) {
        JSONObject root = readObject(context, path);
        if (root == null) {
            return new JSONArray();
        }
        JSONArray array = root.optJSONArray(field);
        return array == null ? new JSONArray() : array;
    }

    private static String read(Context context, String path) {
        try (InputStream input = context.getAssets().open(path)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(8192);
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.e(TAG, "impossible de lire " + path, e);
            return null;
        }
    }
}