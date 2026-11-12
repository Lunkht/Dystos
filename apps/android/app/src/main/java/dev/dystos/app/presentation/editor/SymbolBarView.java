package dev.dystos.app.presentation.editor;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import dev.dystos.app.R;
import dev.dystos.app.ide.Language;

/**
 * Barre de symboles rapides.
 *
 * Taper ces signes sur un clavier mobile est long et imprécis ; les mettre
 * à portée de pouce rend le code réellement saisissable. Les symboles et leur
 * expansion (espaces, doublons) viennent du langage actif.
 */
public final class SymbolBarView extends LinearLayout {

    private CodeEditText editor;
    private Language language;

    public SymbolBarView(@NonNull Context context) {
        this(context, null);
    }

    public SymbolBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
    }

    /** Rattache la barre à son champ et au langage actif, et reconstruit les symboles. */
    public void bind(Language language, CodeEditText editor) {
        this.language = language;
        this.editor = editor;
        removeAllViews();
        if (language != null) {
            buildButtons(language.symbols());
        }
    }

    private void buildButtons(List<String> symbols) {
        float density = getResources().getDisplayMetrics().density;
        int margin = (int) (2 * density);

        for (String symbol : symbols) {
            TextView key = new TextView(getContext());
            key.setText(symbol);
            key.setTextSize(15);
            key.setTypeface(Typeface.MONOSPACE);
            key.setGravity(Gravity.CENTER);
            key.setTextColor(getResources().getColor(
                    R.color.dystos_on_surface, getContext().getTheme()));
            key.setBackgroundResource(android.R.drawable.list_selector_background);
            key.setPadding((int) (10 * density), (int) (8 * density),
                    (int) (10 * density), (int) (8 * density));
            LayoutParams params = new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(margin);
            key.setLayoutParams(params);
            key.setTag(symbol);
            key.setOnClickListener(view -> insert(symbol));
            addView(key);
        }
    }

    private void insert(String symbol) {
        if (editor == null) {
            return;
        }
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start < 0 || end < 0) {
            return;
        }
        int insertAt = Math.min(start, end);
        String before = editor.getText().subSequence(0, insertAt).toString();
        String text = language == null
                ? symbol : language.expand(symbol, before);
        editor.getText().replace(insertAt, Math.max(start, end), text);
        editor.setSelection(insertAt + text.length());
    }
}