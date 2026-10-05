package dev.dystos.app.core.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Lecteur JSON minimal, avec branche explicite sur chaque champ optionnel.
 *
 * L'application embarque tout le contenu et n'a besoin que de lire des objets
 * JSON produits par tools/build-content. Aucune dépendance externe.
 */
public final class Json {

    private Json() {
    }

    public static JSONObject object(String source) {
        try {
            return new JSONObject(source);
        } catch (JSONException e) {
            throw new IllegalStateException("JSON invalide", e);
        }
    }

    public static JSONArray array(String source) {
        try {
            return new JSONArray(source);
        } catch (JSONException e) {
            throw new IllegalStateException("JSON invalide", e);
        }
    }

    public static String optString(JSONObject json, String key, String fallback) {
        if (!json.has(key) || json.isNull(key)) {
            return fallback;
        }
        String value = json.optString(key, fallback);
        return value == null ? fallback : value;
    }

    public static int optInt(JSONObject json, String key, int fallback) {
        return json.has(key) && !json.isNull(key) ? json.optInt(key, fallback) : fallback;
    }

    public static JSONObject optObject(JSONObject json, String key) {
        return json.has(key) && !json.isNull(key) ? json.optJSONObject(key) : null;
    }

    public static JSONArray optArray(JSONObject json, String key) {
        return json.has(key) && !json.isNull(key) ? json.optJSONArray(key) : new JSONArray();
    }

    /**
     * Élément objet d'un tableau, ou null si l'élément est absent ou mal
     * formé. Un contenu corrompu ne doit pas faire planter l'application.
     */
    public static JSONObject optObject(JSONArray array, int index) {
        if (index < 0 || index >= array.length()) {
            return null;
        }
        return array.optJSONObject(index);
    }

    /** Convertit un tableau JSON en liste de chaînes. */
    public static List<String> toStringList(JSONArray array) {
        List<String> values = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            values.add(array.optString(i, ""));
        }
        return values;
    }

    /** Convertit un objet JSON en map, en ignorant les valeurs non textuelles. */
    public static Map<String, Boolean> toBooleanMap(JSONObject json) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (java.util.Iterator<String> keys = json.keys(); keys.hasNext(); ) {
            String key = keys.next();
            map.put(key, json.optBoolean(key, false));
        }
        return map;
    }
}