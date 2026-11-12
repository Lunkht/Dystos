package dev.dystos.app.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Réponses au questionnaire d'onboarding (profil, compétences, objectifs).
 *
 * Les choix sont conservés localement pour être relus dans le profil :
 * rien ne part sur le réseau.
 */
public final class SurveyPreferences {

    private static final String FILE = "dystos_survey";
    private static final String KEY_ANSWER_PREFIX = "answer_";
    private static final String KEY_SKILLS = "skills";
    private static final String KEY_COMPLETED = "completed";

    private SurveyPreferences() {
    }

    /** Enregistre la réponse choisie à une étape donnée. */
    public static void saveAnswer(Context context, int step, String answer) {
        prefs(context).edit()
                .putString(KEY_ANSWER_PREFIX + step, answer)
                .apply();
    }

    /** Réponse d'une étape, chaîne vide si l'étape n'a pas été répondue. */
    public static String answer(Context context, int step) {
        String value = prefs(context).getString(KEY_ANSWER_PREFIX + step, "");
        return value == null ? "" : value;
    }

    /** Enregistre les compétences sélectionnées (étape « compétences »). */
    public static void saveSkills(Context context, Set<String> skills) {
        prefs(context).edit()
                .putStringSet(KEY_SKILLS, new HashSet<>(skills))
                .apply();
    }

    /** Compétences sélectionnées, vide si aucune. */
    public static Set<String> skills(Context context) {
        Set<String> values = prefs(context).getStringSet(KEY_SKILLS, Collections.emptySet());
        return values == null ? Collections.emptySet() : values;
    }

    /** Marque le questionnaire comme terminé. */
    public static void setCompleted(Context context, boolean completed) {
        prefs(context).edit().putBoolean(KEY_COMPLETED, completed).apply();
    }

    /** Indique si le questionnaire a été terminé. */
    public static boolean isCompleted(Context context) {
        return prefs(context).getBoolean(KEY_COMPLETED, false);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
