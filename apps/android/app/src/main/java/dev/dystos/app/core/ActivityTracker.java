package dev.dystos.app.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Suivi des jours d'activité (série / streak).
 *
 * Une journée est « active » dès qu'une leçon est lue ou un exercice résolu
 * ce jour-là. L'historique est conservé localement, en préférences.
 */
public final class ActivityTracker {

    private static final String FILE = "dystos_activity";
    private static final String KEY_DAYS = "jours";
    private static final String KEY_BEST_STREAK = "record";
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private ActivityTracker() {
    }

    /** Note l'activité du jour. Appel plusieurs fois dans la journée : idempotent. */
    public static void record(Context context) {
        SharedPreferences prefs = prefs(context);
        Set<String> days = days(prefs);
        String today = LocalDate.now().format(FORMAT);
        if (!days.add(today)) {
            return;
        }
        int streak = streakOf(days, LocalDate.now());
        prefs.edit()
                .putStringSet(KEY_DAYS, days)
                .putInt(KEY_BEST_STREAK,
                        Math.max(prefs.getInt(KEY_BEST_STREAK, 0), streak))
                .apply();
    }

    /** Nombre de jours actifs au total. */
    public static int totalDays(Context context) {
        return days(prefs(context)).size();
    }

    /** Série en cours : jours d'activité consécutifs jusqu'à aujourd'hui (ou hier). */
    public static int streak(Context context) {
        return streakOf(days(prefs(context)), LocalDate.now());
    }

    /** Meilleure série jamais atteinte. */
    public static int bestStreak(Context context) {
        return prefs(context).getInt(KEY_BEST_STREAK, 0);
    }

    /** Vrai si la date indiquée est un jour actif. */
    public static boolean isActive(Context context, LocalDate date) {
        return days(prefs(context)).contains(date.format(FORMAT));
    }

    /** Jours de la semaine en cours, du lundi au dimanche. */
    public static List<LocalDate> currentWeek() {
        LocalDate monday = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<LocalDate> week = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            week.add(monday.plusDays(i));
        }
        return week;
    }

    /** Compte les jours consécutifs, en partant d'aujourd'hui (ou d'hier). */
    private static int streakOf(Set<String> days, LocalDate today) {
        LocalDate cursor = days.contains(today.format(FORMAT))
                ? today
                : today.minusDays(1);
        int streak = 0;
        while (days.contains(cursor.format(FORMAT))) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private static Set<String> days(SharedPreferences prefs) {
        Set<String> values = prefs.getStringSet(KEY_DAYS, Collections.emptySet());
        return values == null ? new HashSet<>() : new HashSet<>(values);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}