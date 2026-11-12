package dev.dystos.app.core;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Préférence du langage de l'IDE.
 *
 * Le langage sélectionné dans les paramètres de l'IDE est partagé entre
 * tous les onglets d'édition.
 */
public final class IdePreferences {

    private static final String FILE = "ide_prefs";
    private static final String KEY_LANGUAGE = "ide_language";
    private static final String KEY_FONT_SIZE = "editor_font_size";
    private static final String KEY_LINE_NUMBERS = "editor_line_numbers";
    private static final String KEY_INDENT_SIZE = "editor_indent_size";
    private static final String KEY_AUTO_INDENT = "editor_auto_indent";
    private static final String KEY_AUTO_CLOSE_PAIRS = "editor_auto_close_pairs";
    private static final String DEFAULT_LANGUAGE = "dystos";
    private static final int DEFAULT_FONT_SIZE = 14;
    private static final int DEFAULT_INDENT_SIZE = 4;

    private IdePreferences() {
    }

    /** Langue actuellement choisie (identifiant du langage de programmation). */
    public static String languageId(Context context) {
        return prefs(context).getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);
    }

    public static void setLanguageId(Context context, String id) {
        prefs(context).edit().putString(KEY_LANGUAGE, id).apply();
    }

    public static int fontSizeSp(Context context) {
        return prefs(context).getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE);
    }

    public static void setFontSizeSp(Context context, int size) {
        prefs(context).edit().putInt(KEY_FONT_SIZE, Math.max(12, Math.min(22, size))).apply();
    }

    public static boolean lineNumbersEnabled(Context context) {
        return prefs(context).getBoolean(KEY_LINE_NUMBERS, true);
    }

    public static void setLineNumbersEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_LINE_NUMBERS, enabled).apply();
    }

    public static int indentSize(Context context) {
        int size = prefs(context).getInt(KEY_INDENT_SIZE, DEFAULT_INDENT_SIZE);
        return size == 2 ? 2 : DEFAULT_INDENT_SIZE;
    }

    public static void setIndentSize(Context context, int size) {
        prefs(context).edit().putInt(KEY_INDENT_SIZE, size == 2 ? 2 : DEFAULT_INDENT_SIZE).apply();
    }

    public static boolean autoIndentEnabled(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_INDENT, true);
    }

    public static void setAutoIndentEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_INDENT, enabled).apply();
    }

    public static boolean autoClosePairsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_CLOSE_PAIRS, true);
    }

    public static void setAutoClosePairsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_CLOSE_PAIRS, enabled).apply();
    }

    public static void reset(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}