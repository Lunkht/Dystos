package dev.dystos.app.presentation.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import dev.dystos.app.R;

/**
 * Colonne des numéros de ligne.
 *
 * Dessiner les numéros plutôt qu'afficher un second champ évite de
 * désynchroniser deux métriques de police.
 */
public final class LineNumberView extends View {

    private static final int ERROR_COLOR = 0xFFF85149;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private CodeEditText editor;
    private int lineCount = 1;
    private int errorLine;
    private int color;

    public LineNumberView(@NonNull Context context) {
        this(context, null);
    }

    public LineNumberView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        color = context.getResources().getColor(
                R.color.dystos_code_comment, context.getTheme());
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setTextSize(dp(13));
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor(color);
    }

    public void attachTo(CodeEditText editor) {
        this.editor = editor;
        countLines(editor);
    }

    public void setErrorLine(int line) {
        this.errorLine = line;
    }

    public void setLineNumberColor(int color) {
        this.color = color;
    }

    private void countLines(CodeEditText editor) {
        Editable text = editor.getText();
        int lines = 1;
        if (text != null) {
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    lines++;
                }
            }
        }
        this.lineCount = lines;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (editor == null) {
            return;
        }
        countLines(editor);

        float x = getWidth() - dp(8);
        float textSize = paint.getTextSize();
        float lineHeight = editor.getLineHeight();
        float top = editor.getPaddingTop() - editor.getScrollY();

        // Seules les lignes visibles sont dessinées.
        int first = (int) ((-top) / lineHeight) + 1;
        if (first < 1) {
            first = 1;
        }
        int last = first + (int) (getHeight() / lineHeight) + 1;

        for (int line = first; line <= lineCount && line <= last; line++) {
            paint.setColor(line == errorLine ? ERROR_COLOR : color);
            float baseline = top + (line - 1) * lineHeight + textSize;
            canvas.drawText(String.valueOf(line), x, baseline, paint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
