package com.vulsoft.dystos.sema;

import java.util.Collection;

/** Suggestions « vouliez-vous dire ... ? » par distance d'édition. */
final class Suggest {

    private Suggest() {
    }

    /** Retourne le candidat le plus proche, ou null s'il n'y en a pas d'assez proche. */
    static String closest(String name, Collection<String> candidates) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        int limit = Math.max(1, Math.min(3, name.length() / 3));
        for (String c : candidates) {
            if (c.equals(name) || c.startsWith("_") && !name.startsWith("_")) {
                continue;
            }
            int d = distance(name, c);
            if (d < bestDistance) {
                bestDistance = d;
                best = c;
            }
        }
        return bestDistance <= limit ? best : null;
    }

    static String hint(String name, Collection<String> candidates) {
        String c = closest(name, candidates);
        return c == null ? "" : " Vouliez-vous dire '" + c + "' ?";
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }
}
