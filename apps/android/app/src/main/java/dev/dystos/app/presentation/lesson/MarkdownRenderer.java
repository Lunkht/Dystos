package dev.dystos.app.presentation.lesson;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

/**
 * Rendu Markdown local, limité aux constructions utilisées par les leçons.
 *
 * Choix : pas de WebView, donc pas de JavaScript et pas de ressource distante.
 * Le rendu est un Spannable : sélectionnable, accessible, et rapide sur un
 * appareil d'entrée de gamme.
 *
 * Constructions gérées : titres, listes, citations, blocs de code, code
 * inline, gras, italique.
 */
final class MarkdownRenderer {

    private static final int CODE_BACKGROUND = 0xFF10161D;
    private static final int CODE_TEXT = 0xFFE6EDF3;

    private MarkdownRenderer() {
    }

    static CharSequence render(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        String normalised = markdown.replace("\r\n", "\n");
        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] lines = normalised.split("\n", -1);
        boolean inFence = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();

            if (line.startsWith("```")) {
                inFence = !inFence;
                continue;
            }

            if (inFence) {
                appendCodeLine(out, rawLine);
                continue;
            }

            if (line.isEmpty()) {
                out.append("\n\n");
                continue;
            }

            if (line.startsWith("#")) {
                appendHeading(out, line);
                continue;
            }

            if (line.startsWith("> ")) {
                appendQuote(out, line.substring(2));
                continue;
            }

            if (line.startsWith("- ")) {
                appendListItem(out, line.substring(2));
                continue;
            }

            appendParagraph(out, line);
        }

        return out;
    }

    private static void appendHeading(SpannableStringBuilder out, String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        int start = out.length();
        out.append(line.substring(level).trim()).append("\n\n");
        int end = out.length() - 1;
        out.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (level <= 2) {
            out.setSpan(new RelativeSizeSpan(1.2f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void appendListItem(SpannableStringBuilder out, String text) {
        int start = out.length();
        out.append("•  ").append(text).append('\n');
        applyInline(out, start, out.length());
    }

    private static void appendQuote(SpannableStringBuilder out, String text) {
        int start = out.length();
        out.append("│ ").append(text).append("\n\n");
        int end = out.length() - 1;
        out.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        applyInline(out, start, end);
    }

    private static void appendParagraph(SpannableStringBuilder out, String text) {
        int start = out.length();
        out.append(text).append("\n\n");
        applyInline(out, start, out.length() - 1);
    }

    private static void appendCodeLine(SpannableStringBuilder out, String line) {
        int start = out.length();
        out.append(line).append('\n');
        int end = out.length();
        out.setSpan(new BackgroundColorSpan(CODE_BACKGROUND), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new ForegroundColorSpan(CODE_TEXT), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new TypefaceSpan("monospace"), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** Applique code inline, gras et italique sur la plage donnée. */
    private static void applyInline(SpannableStringBuilder out, int start, int end) {
        String text = out.toString();

        int index = start;
        while (index < end) {
            int tick = text.indexOf('`', index);
            if (tick < 0 || tick >= end) {
                break;
            }
            int closing = text.indexOf('`', tick + 1);
            if (closing < 0 || closing >= end) {
                break;
            }
            int codeEnd = closing + 1;
            out.setSpan(new ForegroundColorSpan(CODE_TEXT), tick, codeEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new TypefaceSpan("monospace"), tick, codeEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            index = codeEnd;
        }

        applyStyle(out, text, start, end, "**", Typeface.BOLD);
        applyStyle(out, text, start, end, "*", Typeface.ITALIC);
    }

    private static void applyStyle(
            SpannableStringBuilder out,
            String text,
            int start,
            int end,
            String marker,
            int style) {
        int markerLength = marker.length();
        int index = start;
        while (index < end) {
            int open = text.indexOf(marker, index);
            if (open < 0 || open + markerLength > end) {
                break;
            }
            int close = text.indexOf(marker, open + markerLength);
            if (close < 0 || close + markerLength > end) {
                break;
            }
            out.setSpan(
                    new StyleSpan(style),
                    open,
                    close + markerLength,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            index = close + markerLength;
        }
    }
}