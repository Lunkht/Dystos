package dev.dystos.app.core;

import android.content.Context;

/**
 * Mémorise le fait que l'introduction a déjà été parcourue.
 *
 * L'introduction ne doit apparaître qu'au tout premier lancement : ensuite
 * l'application démarre directement sur la liste des leçons.
 */
public final class OnboardingPreferences {

    private static final String KEY_SEEN = "onboarding_seen";

    private OnboardingPreferences() {
    }

    /** Indique si l'introduction a déjà été terminée. */
    public static boolean isSeen(Context context) {
        return ThemePreferences.prefs(context).getBoolean(KEY_SEEN, false);
    }

    /** Marque l'introduction comme terminée. */
    public static void setSeen(Context context) {
        ThemePreferences.prefs(context).edit().putBoolean(KEY_SEEN, true).apply();
    }
}