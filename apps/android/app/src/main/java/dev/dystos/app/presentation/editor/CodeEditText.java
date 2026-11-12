package dev.dystos.app.presentation.editor;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

import dev.dystos.app.R;
import dev.dystos.app.core.IdePreferences;
import dev.dystos.app.ide.Language;
import dev.dystos.app.language.CodeAnalysis;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Éditeur de code multi-langage.
 *
 * Un champ de texte ne sait ni colorer le code ni afficher les numéros de
 * ligne. Les deux sont ajoutés ici : la coloration vient de l'analyse réelle
 * du programme par le langage actif, et les numéros sont dessinés dans la
 * marge.
 *
 * La coloration est reportée de quelques centaines de millisecondes après la
 * dernière frappe : taper vite ne déclenche qu'une seule analyse. Les couleurs
 * sont appliquées directement sur l'Editable (sans reconstruire le texte),
 * ce qui garde le curseur, la sélection et l'état du clavier stables.
 */
public final class CodeEditText extends AppCompatEditText {

    private static final long HIGHLIGHT_DELAY_MS = 250L;
    private static final int HISTORY_LIMIT = 100;
    private final Map<CodeAnalysis.Highlight, Integer> palette = new HashMap<>();
    private Handler handler;
    private final Runnable highlightTask = this::applyHighlight;

    private final ArrayDeque<String> undoStack = new ArrayDeque<>();
    private final ArrayDeque<String> redoStack = new ArrayDeque<>();
    private boolean applyingHistory;
    private boolean autoAdjusting;
    private boolean autoIndentEnabled = true;
    private boolean autoClosePairsEnabled = true;
    private int indentSize = 4;

    private Language language;
    private LineNumberView lineNumbers;
    private CodeAnalysis.Problem problem;
    private int lineNumberColor;

    public CodeEditText(@NonNull Context context) {
        this(context, null);
    }

    public CodeEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, androidx.appcompat.R.attr.editTextStyle);
    }

    public CodeEditText(@NonNull Context context, @Nullable AttributeSet attrs,
                        int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setTypeface(Typeface.MONOSPACE);
        setGravity(Gravity.TOP | Gravity.START);
        setHorizontallyScrolling(false);
        setPadding(dp(8), dp(8), dp(8), dp(8));

        // Snapshot avant chaque modification pour l'annulation : on ne peut pas
        // surcharger beforeTextChanged (final dans EditText), on observe donc
        // les TextWatchers.
        addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                if (!applyingHistory && (count > 0 || after > 0)) {
                    pushUndo(text.toString());
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // L'éditeur est posé sur un fond de bloc de code : la couleur de base
        // couvre les caractères que l'analyse ne tokenise pas (espaces,
        // ponctuation inconnue), la coloration vient par dessus.
        int textColor = getResources().getColor(
                R.color.dystos_code_on_background, getContext().getTheme());
        int commentColor = getResources().getColor(
                R.color.dystos_code_comment, getContext().getTheme());
        setTextColor(textColor);
        setHintTextColor(commentColor);
        setHighlightColor(0x44FFFFFF);
        lineNumberColor = commentColor;

        palette.put(CodeAnalysis.Highlight.PLAIN, textColor);
        palette.put(CodeAnalysis.Highlight.KEYWORD, getResources().getColor(
                R.color.dystos_code_keyword, getContext().getTheme()));
        palette.put(CodeAnalysis.Highlight.STRING, getResources().getColor(
                R.color.dystos_code_string, getContext().getTheme()));
        palette.put(CodeAnalysis.Highlight.NUMBER, getResources().getColor(
                R.color.dystos_code_number, getContext().getTheme()));
        palette.put(CodeAnalysis.Highlight.OPERATOR, getResources().getColor(
                R.color.dystos_code_operator, getContext().getTheme()));
        palette.put(CodeAnalysis.Highlight.BUILTIN, getResources().getColor(
                R.color.dystos_code_builtin, getContext().getTheme()));
        palette.put(CodeAnalysis.Highlight.IDENTIFIER, textColor);
        palette.put(CodeAnalysis.Highlight.COMMENT, commentColor);
    }

    /** Change le langage analysé : la prochaine coloration suit le lexer. */
    public void setLanguage(Language language) {
        this.language = language;
        setProblem(null);
        scheduleHighlight();
    }

    /** Rattache la colonne des numéros de ligne. */
    public void setLineNumbers(LineNumberView view) {
        this.lineNumbers = view;
        if (view != null) {
            view.attachTo(this);
            view.setLineNumberColor(lineNumberColor);
            view.setErrorLine(problem == null ? 0 : problem.line());
        }
    }

    /** Applique les préférences partagées par les écrans IDE, exemples et exercices. */
    public void applyIdePreferences() {
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,
                IdePreferences.fontSizeSp(getContext()));
        indentSize = IdePreferences.indentSize(getContext());
        autoIndentEnabled = IdePreferences.autoIndentEnabled(getContext());
        autoClosePairsEnabled = IdePreferences.autoClosePairsEnabled(getContext());
        if (lineNumbers != null) {
            lineNumbers.setVisibility(IdePreferences.lineNumbersEnabled(getContext())
                    ? VISIBLE : GONE);
        }
    }

    public void setLineNumberColor(int color) {
        this.lineNumberColor = color;
        if (lineNumbers != null) {
            lineNumbers.setLineNumberColor(color);
        }
    }

    /** La dernière erreur d'analyse, affichée en direct. */
    public CodeAnalysis.Problem getProblem() {
        return problem;
    }

    @Override
    protected void onTextChanged(CharSequence s, int start, int before, int count) {
        super.onTextChanged(s, start, before, count);
        if (!autoAdjusting) {
            autoAdjust(s, start, before);
        }
        scheduleHighlight();
        if (lineNumbers != null) {
            lineNumbers.invalidate();
        }
    }

    /** Fermeture automatique des paires et indentation après Entrée. */
    private void autoAdjust(CharSequence s, int start, int before) {
        if (before != 0 || s.length() == 0 || start >= s.length()) {
            return;
        }
        char typed = s.charAt(start);
        String closer = autoClosePairsEnabled ? closingFor(typed) : null;
        if (closer != null && getSelectionStart() == getSelectionEnd()) {
            Editable editable = getText();
            if (editable != null) {
                autoAdjusting = true;
                editable.insert(start + 1, closer);
                autoAdjusting = false;
                setSelection(start + 1);
            }
            return;
        }
        if (autoIndentEnabled && typed == '\n') {
            String indent = nextIndent(s, start);
            if (!indent.isEmpty()) {
                Editable editable = getText();
                if (editable != null) {
                    autoAdjusting = true;
                    editable.insert(start + 1, indent);
                    autoAdjusting = false;
                    setSelection(start + 1 + indent.length());
                }
            }
        }
    }

    private static String closingFor(char c) {
        switch (c) {
            case '(':
                return ")";
            case '[':
                return "]";
            case '{':
                return "}";
            case '"':
                return "\"";
            default:
                return null;
        }
    }

    /** Indentation de la ligne précédente, plus un niveau si un bloc s'ouvre. */
    private String nextIndent(CharSequence s, int newlineIndex) {
        int lineStart = newlineIndex;
        while (lineStart > 0 && s.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }
        StringBuilder indent = new StringBuilder();
        for (int i = lineStart; i < newlineIndex; i++) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t') {
                break;
            }
            indent.append(c);
        }
        int last = newlineIndex - 1;
        while (last >= lineStart && (s.charAt(last) == ' ' || s.charAt(last) == '\t')) {
            last--;
        }
        if (last >= lineStart) {
            char c = s.charAt(last);
            if (c == '{' || c == '[' || c == '(' || c == ':') {
                for (int i = 0; i < indentSize; i++) {
                    indent.append(' ');
                }
            }
        }
        return indent.toString();
    }

    private void pushUndo(String snapshot) {
        undoStack.addLast(snapshot);
        if (undoStack.size() > HISTORY_LIMIT) {
            undoStack.removeFirst();
        }
        redoStack.clear();
    }

    public boolean hasUndo() {
        return !undoStack.isEmpty();
    }

    public boolean hasRedo() {
        return !redoStack.isEmpty();
    }

    /** Oublie l'historique (changement de fichier, réinitialisation). */
    public void resetHistory() {
        undoStack.clear();
        redoStack.clear();
    }

    public void undo() {
        applyHistory(undoStack, redoStack);
    }

    public void redo() {
        applyHistory(redoStack, undoStack);
    }

    private void applyHistory(ArrayDeque<String> from, ArrayDeque<String> to) {
        if (from.isEmpty()) {
            return;
        }
        String snapshot = getText() == null ? "" : getText().toString();
        String target = from.removeLast();
        to.addLast(snapshot);
        if (to.size() > HISTORY_LIMIT) {
            to.removeFirst();
        }
        applyingHistory = true;
        int targetLength = target.length();
        setText(target);
        setSelection(targetLength);
        applyingHistory = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (handler != null) {
            handler.removeCallbacks(highlightTask);
        }
        super.onDetachedFromWindow();
    }

    /** Reporte la coloration : des frappes rapprochées ne coûtent qu'une analyse. */
    private void scheduleHighlight() {
        if (handler == null) {
            handler = new Handler(Looper.getMainLooper());
        }
        handler.removeCallbacks(highlightTask);
        handler.postDelayed(highlightTask, HIGHLIGHT_DELAY_MS);
    }

    /** Colore le texte courant à partir de l'analyse du langage actif. */
    private void applyHighlight() {
        Editable editable = getText();
        if (editable == null) {
            return;
        }
        if (language == null || editable.length() == 0) {
            clearSpans(editable);
            setProblem(null);
            return;
        }

        CodeAnalysis.Result result = language.analyse(editable.toString());
        setProblem(result.problem());

        clearSpans(editable);
        for (CodeAnalysis.Span span : result.spans()) {
            int start = Math.max(0, Math.min(span.start(), editable.length()));
            int end = Math.max(start, Math.min(span.end(), editable.length()));
            if (end <= start) {
                continue;
            }
            Integer color = palette.get(span.highlight());
            if (color != null) {
                editable.setSpan(new ForegroundColorSpan(color), start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    /** Efface les couleurs posées précédemment, sans toucher au texte. */
    private void clearSpans(Editable editable) {
        ForegroundColorSpan[] existing =
                editable.getSpans(0, editable.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : existing) {
            editable.removeSpan(span);
        }
    }

    private void setProblem(CodeAnalysis.Problem problem) {
        this.problem = problem;
        if (lineNumbers != null) {
            lineNumbers.setErrorLine(problem == null ? 0 : problem.line());
            lineNumbers.invalidate();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}