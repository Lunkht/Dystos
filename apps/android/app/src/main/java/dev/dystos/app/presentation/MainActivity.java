package dev.dystos.app.presentation;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import dev.dystos.app.R;
import dev.dystos.app.core.ThemePreferences;
import dev.dystos.app.presentation.chapter.ChapterListFragment;
import dev.dystos.app.presentation.lesson.LessonReaderFragment;

/** Activité unique de l'application. */
public final class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new ChapterListFragment())
                    .commit();
        }
    }

    /** Ouvre la lecture d'une leçon. */
    public void openLesson(String lessonId) {
        Fragment fragment = LessonReaderFragment.newInstance(lessonId);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(lessonId)
                .commit();
    }

    /** Bascule entre le thème clair et le thème sombre. */
    public void toggleTheme() {
        ThemePreferences.setDark(this, !ThemePreferences.isDark(this));
    }
}