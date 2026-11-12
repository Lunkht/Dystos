package dev.dystos.app.presentation;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import dev.dystos.app.R;
import dev.dystos.app.core.OnboardingPreferences;
import dev.dystos.app.core.ThemePreferences;
import dev.dystos.app.presentation.chapter.ChapterListFragment;
import dev.dystos.app.presentation.example.CodeFragment;
import dev.dystos.app.presentation.example.ExampleListFragment;
import dev.dystos.app.presentation.exercise.ExerciseEditorFragment;
import dev.dystos.app.presentation.ide.IdeFragment;
import dev.dystos.app.presentation.lesson.LessonReaderFragment;
import dev.dystos.app.presentation.onboarding.OnboardingFragment;
import dev.dystos.app.presentation.quiz.QuizFragment;
import dev.dystos.app.presentation.quiz.QuizListFragment;
import dev.dystos.app.presentation.reference.ReferenceFragment;
import dev.dystos.app.presentation.search.SearchFragment;
import dev.dystos.app.presentation.splash.SplashFragment;

/** Activité unique de l'application. */
public final class MainActivity extends AppCompatActivity {

    // Tags stables pour retrouver chaque onglet dans le back-stack
    private static final String TAG_MISSION     = "tab_mission";
    private static final String TAG_IDE         = "tab_ide";
    private static final String TAG_REVISION    = "tab_revision";
    private static final String TAG_SETTINGS    = "tab_settings";

    private View bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);

        findViewById(R.id.tab_mission).setOnClickListener(v -> showTab(R.id.nav_mission));
        findViewById(R.id.tab_ide).setOnClickListener(v -> showTab(R.id.nav_ide));
        findViewById(R.id.tab_revision).setOnClickListener(v -> showTab(R.id.nav_revision));
        findViewById(R.id.tab_settings).setOnClickListener(v -> showTab(R.id.nav_settings));

        if (savedInstanceState == null) {
            bottomNav.setVisibility(View.GONE);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new SplashFragment(), TAG_MISSION)
                    .commit();
        }
    }

    // ─── Navigation par onglets ───────────────────────────────────────────────

    private void showTab(int itemId) {
        updateTabSelection(itemId);
        if (itemId == R.id.nav_mission) {
            switchRootFragment(new ChapterListFragment(), TAG_MISSION);
        } else if (itemId == R.id.nav_ide) {
            switchRootFragment(new IdeFragment(), TAG_IDE);
        } else if (itemId == R.id.nav_revision) {
            switchRootFragment(new RevisionFragment(), TAG_REVISION);
        } else if (itemId == R.id.nav_settings) {
            switchRootFragment(new ParametresFragment(), TAG_SETTINGS);
        }
    }

    private void updateTabSelection(int activeId) {
        int primaryColor = getResources().getColor(R.color.dystos_primary, getTheme());
        int outlineColor = getResources().getColor(R.color.dystos_outline, getTheme());

        ImageView iconMission = findViewById(R.id.icon_mission);
        ImageView iconIde = findViewById(R.id.icon_ide);
        ImageView iconRevision = findViewById(R.id.icon_revision);
        ImageView iconSettings = findViewById(R.id.icon_settings);

        TextView textMission = findViewById(R.id.text_mission);
        TextView textIde = findViewById(R.id.text_ide);
        TextView textRevision = findViewById(R.id.text_revision);
        TextView textSettings = findViewById(R.id.text_settings);

        if (iconMission != null && textMission != null) setTabState(iconMission, textMission, activeId == R.id.nav_mission, primaryColor, outlineColor);
        if (iconIde != null && textIde != null) setTabState(iconIde, textIde, activeId == R.id.nav_ide, primaryColor, outlineColor);
        if (iconRevision != null && textRevision != null) setTabState(iconRevision, textRevision, activeId == R.id.nav_revision, primaryColor, outlineColor);
        if (iconSettings != null && textSettings != null) setTabState(iconSettings, textSettings, activeId == R.id.nav_settings, primaryColor, outlineColor);
    }

    private void setTabState(ImageView icon, TextView text, boolean active, int primaryColor, int outlineColor) {
        int color = active ? primaryColor : outlineColor;
        icon.setColorFilter(color);
        text.setTextColor(color);
        text.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
    }

    /**
     * Remplace le fragment racine (sans empiler dans le back-stack).
     * Le back-stack des écrans enfants (leçon, exercice…) est vidé pour
     * garder une navigation claire entre les onglets.
     */
    private void switchRootFragment(Fragment fragment, String tag) {
        // Vider le back-stack des écrans enfants avant de changer d'onglet
        getSupportFragmentManager().popBackStack(
                null,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);

        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.screen_fade_in, R.anim.screen_fade_out)
                .replace(R.id.fragment_container, fragment, tag)
                .commit();
    }

    // ─── Méthodes de navigation vers les écrans enfants ──────────────────────

    /** Affiche la bottom nav après l'onboarding. */
    public void showMainNavigation() {
        bottomNav.setVisibility(View.VISIBLE);
        showTab(R.id.nav_mission);
    }

    /** Retour à l'écran d'accueil (onglet Mission). */
    public void goHome() {
        showTab(R.id.nav_mission);
    }

    public void openProfile() {
        push(new ProfileFragment(), "profile");
    }

    public void openLogin() {
        push(new dev.dystos.app.presentation.auth.LoginFragment(), "login");
    }

    public void openRegister() {
        push(new dev.dystos.app.presentation.auth.RegisterFragment(), "register");
    }

    /** Écran de fin d'onboarding : connexion, inscription ou entrée directe. */
    public void openAccountPrompt() {
        push(new dev.dystos.app.presentation.auth.AccountPromptFragment(), "account_prompt");
    }

    public void openFaq() {
        push(new dev.dystos.app.presentation.settings.FaqFragment(), "faq");
    }

    public void openAbout() {
        push(new dev.dystos.app.presentation.settings.AboutFragment(), "about");
    }

    public void openUpdateCheck() {
        push(new dev.dystos.app.presentation.settings.UpdateCheckFragment(), "update_check");
    }

    /** Paramètres de l'IDE : langage de programmation par défaut de l'éditeur. */
    public void openIdeSettings() {
        push(new dev.dystos.app.presentation.settings.IdeSettingsFragment(), "ide_settings");
    }

    /** Open lesson */
    public void openLesson(String lessonId) {
        push(LessonReaderFragment.newInstance(lessonId), "lecon:" + lessonId);
    }

    /** Open exercise */
    public void openExercise(String lessonId) {
        push(ExerciseEditorFragment.newInstance(lessonId), "exercice:" + lessonId);
    }

    public void openExample(String exampleId) {
        push(CodeFragment.newInstance(exampleId), "exemple:" + exampleId);
    }

    public void openExamples() {
        push(new ExampleListFragment(), "exemples");
    }

    public void openSearch() {
        push(new SearchFragment(), "recherche");
    }

    public void openReference() {
        push(new ReferenceFragment(), "reference");
    }

    public void openQuizzes() {
        push(new QuizListFragment(), "quiz");
    }

    public void openQuiz(String quizId) {
        push(QuizFragment.newInstance(quizId), "quiz:" + quizId);
    }

    private void push(Fragment fragment, String backStackName) {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.screen_slide_in_right,
                        R.anim.screen_slide_out_left,
                        R.anim.screen_slide_in_left,
                        R.anim.screen_slide_out_right)
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(backStackName)
                .commit();
    }

    public void toggleTheme() {
        ThemePreferences.setDark(this, !ThemePreferences.isDark(this));
    }
}
