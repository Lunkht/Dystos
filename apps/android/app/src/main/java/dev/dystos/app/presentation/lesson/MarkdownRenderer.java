package dev.dystos.app.presentation.lesson;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineBackgroundSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import java.util.EnumMap;
import java.util.Map;

import dev.dystos.app.R;
import dev.dystos.app.language.CodeAnalysis;

/**
 * Rendu Markdown local, limité aux constructions utilisées par les leçons.
 *
 * Choix : pas de WebView, donc pas de JavaScript et pas de ressource distante.
 * Le rendu est un Spannable : sélectionnable, accessible, et rapide sur un
 * appareil d'entrée de gamme.
 *
 * Constructions gérées : titres, listes, citations, blocs de code, code
 * inline, gras, italique.
 *
 * Les blocs de code sont délimités par des triples accents grave : ils sont
 * découpés en blocs entiers, peints sur toute la largeur de la colonne de
 * lecture, puis coloriés avec la coloration syntaxique réelle du langage
 * ({@link CodeAnalysis}), la même que celle de l'éditeur.
 */
public final class MarkdownRenderer {

    private MarkdownRenderer() {
    }

    /** Rend un texte Markdown en Spannable, sans WebView ni JavaScript. */
    public static CharSequence render(Context context, String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        Palette palette = Palette.of(context);
        String normalised = markdown.replace("\r\n", "\n");
        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] lines = normalised.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            if (line.startsWith("```")) {
                int from = i + 1;
                int to = from;
                while (to < lines.length && !lines[to].trim().startsWith("```")) {
                    to++;
                }
                appendCodeBlock(out, lines, from, to, palette);
                i = to;
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
                appendQuote(out, line.substring(2), palette);
                continue;
            }

            if (line.startsWith("- ")) {
                appendListItem(out, line.substring(2), palette);
                continue;
            }

            appendParagraph(out, line, palette);
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

    private static void appendListItem(
            SpannableStringBuilder out, String text, Palette palette) {
        int start = out.length();
        out.append("•  ").append(text).append('\n');
        applyInline(out, start, out.length(), palette);
    }

    private static void appendQuote(
            SpannableStringBuilder out, String text, Palette palette) {
        int start = out.length();
        out.append("│ ").append(text).append("\n\n");
        int end = out.length() - 1;
        out.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        applyInline(out, start, end, palette);
    }

    private static void appendParagraph(
            SpannableStringBuilder out, String text, Palette palette) {
        int start = out.length();
        out.append(text).append("\n\n");
        applyInline(out, start, out.length() - 1, palette);
    }

    /**
     * Ajoute un bloc de code entier : fond pleine largeur, retrait du texte,
     * et coloration syntaxique fournie par l'analyse du langage.
     */
    private static void appendCodeBlock(SpannableStringBuilder out, String[] lines,
                                        int from, int to, Palette palette) {
        if (from >= to) {
            return;
        }
        // Une ligne vide sépare toujours le bloc du texte qui le précède.
        if (out.length() > 0 && !out.toString().endsWith("\n\n")) {
            out.append('\n');
        }

        int blockStart = out.length();
        StringBuilder source = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (i > from) {
                source.append('\n');
            }
            source.append(lines[i]);
            out.append(lines[i]).append('\n');
        }
        int blockEnd = out.length();

        out.setSpan(new TypefaceSpan("monospace"), blockStart, blockEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new ForegroundColorSpan(palette.blockText), blockStart, blockEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new LeadingMarginSpan.Standard(palette.textInset), blockStart, blockEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new CodeBlockSpan(palette.blockBackground, palette.blockPadding),
                blockStart, blockEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        applySyntaxColors(out, blockStart, source.toString(), palette);
        out.append('\n');
    }

    /**
     * Colore le bloc à partir de l'analyse réelle du code.
     *
     * Les positions de {@link CodeAnalysis} sont exprimées dans le texte du
     * bloc : on les décale d'un seul et même offset, celui du début du bloc
     * dans le document rendu.
     */
    private static void applySyntaxColors(SpannableStringBuilder out, int blockStart,
                                          String source, Palette palette) {
        int limit = blockStart + source.length();
        for (CodeAnalysis.Span span : CodeAnalysis.analyse(source).spans()) {
            int start = Math.max(blockStart, blockStart + span.start());
            int end = Math.min(limit, blockStart + span.end());
            if (end <= start) {
                continue;
            }
            Integer color = palette.syntax.get(span.highlight());
            if (color != null) {
                out.setSpan(new ForegroundColorSpan(color), start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    /** Applique code inline, gras et italique sur la plage donnée. */
    private static void applyInline(
            SpannableStringBuilder out, int start, int end, Palette palette) {
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
            out.setSpan(new BackgroundColorSpan(palette.inlineBackground),
                    tick, codeEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new ForegroundColorSpan(palette.inlineText),
                    tick, codeEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new TypefaceSpan("monospace"),
                    tick, codeEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
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

    /**
     * Palette des blocs de code.
     *
     * Toutes les couleurs viennent des ressources : le thème clair et le
     * thème sombre peuvent donc proposer deux colorations distinctes sans
     * aucune condition dans le code.
     */
    private static final class Palette {

        final int blockBackground;
        final int blockText;
        final int inlineBackground;
        final int inlineText;
        final int textInset;
        final int blockPadding;
        final Map<CodeAnalysis.Highlight, Integer> syntax =
                new EnumMap<>(CodeAnalysis.Highlight.class);

        private Palette(Context context) {
            Resources resources = context.getResources();
            Resources.Theme theme = context.getTheme();
            float density = resources.getDisplayMetrics().density;

            blockBackground = resources.getColor(R.color.dystos_code_background, theme);
            blockText = resources.getColor(R.color.dystos_code_on_background, theme);
            inlineBackground = resources.getColor(
                    R.color.dystos_code_inline_background, theme);
            inlineText = resources.getColor(R.color.dystos_code_inline_text, theme);
            textInset = Math.round(14f * density);
            blockPadding = Math.round(6f * density);

            syntax.put(CodeAnalysis.Highlight.PLAIN, blockText);
            syntax.put(CodeAnalysis.Highlight.IDENTIFIER, blockText);
            syntax.put(CodeAnalysis.Highlight.KEYWORD,
                    resources.getColor(R.color.dystos_code_keyword, theme));
            syntax.put(CodeAnalysis.Highlight.BUILTIN,
                    resources.getColor(R.color.dystos_code_builtin, theme));
            syntax.put(CodeAnalysis.Highlight.STRING,
                    resources.getColor(R.color.dystos_code_string, theme));
            syntax.put(CodeAnalysis.Highlight.NUMBER,
                    resources.getColor(R.color.dystos_code_number, theme));
            syntax.put(CodeAnalysis.Highlight.COMMENT,
                    resources.getColor(R.color.dystos_code_comment, theme));
            syntax.put(CodeAnalysis.Highlight.OPERATOR,
                    resources.getColor(R.color.dystos_code_operator, theme));
        }

        static Palette of(Context context) {
            return new Palette(context);
        }
    }

    /**
     * Fond d'un bloc de code.
     *
     * Le rectangle déborde volontairement du texte : le canevas est rogné aux
     * bords de la vue, le fond couvre donc toute la largeur de la colonne de
     * lecture, quel que soit le nombre de caractères de la ligne. L'agrandir
     * verticalement à chaque ligne relie les lignes voisines en un seul bloc,
     * marge haute et basse comprises.
     */
    private static final class CodeBlockSpan implements LineBackgroundSpan {

        private static final float OVERSHOOT = 1 << 20;

        private final Paint paint = new Paint();
        private final int padding;

        CodeBlockSpan(int background, int padding) {
            paint.setColor(background);
            this.padding = padding;
        }

        /**
         * Signature utilisée avant Android 11 : les anciens systèmes ne
         * connaissent pas la version enrichie, on implémente donc les deux.
         */
        public void drawBackground(Canvas canvas, Paint p, int left, int right,
                                   int top, int bottom, int baseline) {
            fill(canvas, left, right, top, bottom);
        }

        @Override
        public void drawBackground(Canvas canvas, Paint p, int left, int right,
                                   int top, int baseline, int bottom,
                                   CharSequence text, int start, int end, int lineNumber) {
            fill(canvas, left, right, top, bottom);
        }

        private void fill(Canvas canvas, int left, int right, int top, int bottom) {
            canvas.drawRect(left - OVERSHOOT, top - padding,
                    right + OVERSHOOT, bottom + padding, paint);
        }
    }
}
