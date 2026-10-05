package dev.dystos.app.core;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Préférence de thème.
 *
 * Un {@link SharedPreferences} suffit : une seule valeur booléenne. Le mode
 * sombre est celui de Dystos, mais l'utilisateur doit pouvoir choisir.
 */
public final class ThemePreferences {

    private static final String FILE = "dystos_prefs";
    private static final String KEY_DARK = "theme_dark";

    private ThemePreferences() {
    }

    /** Applique le thème mémorisé. À appeler au démarrage de l'application. */
    public static void apply(Context context) {
        AppCompatDelegate.setDefaultNightMode(read(context)
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    public static boolean isDark(Context context) {
        return read(context);
    }

    public static void setDark(Context context, boolean dark) {
        prefs(context).edit().putBoolean(KEY_DARK, dark).apply();
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private static boolean read(Context context) {
        return prefs(context).getBoolean(KEY_DARK, true);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}