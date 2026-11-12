package dev.dystos.app.presentation;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.dystos.app.R;
import dev.dystos.app.core.SurveyPreferences;
import dev.dystos.app.databinding.FragmentProfileBinding;
import dev.dystos.app.di.ServiceLocator;
import dev.dystos.app.presentation.chapter.ChapterListViewModel;

/**
 * Onglet Profil : avatar, statistiques (leçons lues, série, complétion)
 * et barre de progression globale.
 */
public final class ProfileFragment extends BaseFragment {

    private FragmentProfileBinding binding;

    @Override
    protected int layoutId() {
        return R.layout.fragment_profile;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentProfileBinding.bind(view);

        view.findViewById(R.id.back_button).setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            } else {
                ((MainActivity) requireActivity()).goHome();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        ChapterListViewModel.State state = new ChapterListViewModel(
                ServiceLocator.getChapter(),
                ServiceLocator.getProgress(),
                ServiceLocator.contentRepository()).load();

        int read = state.getReadLessons();
        int total = state.getTotalLessons();
        int percent = Math.round(state.getRatio() * 100f);

        binding.statLessons.setText(String.valueOf(read));
        binding.statStreak.setText("0"); // Pas encore de système de streak
        binding.statCompletion.setText(percent + "%");
        binding.progressLabel.setText(
                getString(R.string.chapter_progress, read, total));
        binding.progressBar.setProgress(percent);

        refreshPreferences();
    }

    /** Affiche les réponses conservées au questionnaire d'onboarding. */
    private void refreshPreferences() {
        ViewGroup card = binding.preferencesCard;
        card.removeAllViews();

        String[] labels = {
                "Profil", "Expérience", "Objectif", "Entourage", "Objectif quotidien"};
        int[] steps = {0, 1, 2, 3, 5};

        boolean answered = false;
        for (int i = 0; i < steps.length; i++) {
            String value = SurveyPreferences.answer(requireContext(), steps[i]);
            if (!value.isEmpty()) {
                addPreferenceRow(card, labels[i], value);
                answered = true;
            }
        }

        if (!answered) {
            addPreferenceRow(card, "Questionnaire",
                    SurveyPreferences.isCompleted(requireContext())
                            ? "Aucune réponse enregistrée"
                            : "Non complété");
        }
    }

    private void addPreferenceRow(ViewGroup card, String label, String value) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(14);
        card.addView(row, params);

        TextView labelView = new TextView(requireContext());
        labelView.setText(label);
        labelView.setTextSize(12);
        labelView.setTextColor(requireContext().getColor(R.color.dystos_on_surface_variant));
        labelView.setAllCaps(true);
        row.addView(labelView);

        TextView valueView = new TextView(requireContext());
        valueView.setText(value);
        valueView.setTextSize(15);
        valueView.setTextColor(requireContext().getColor(R.color.dystos_on_surface));
        valueView.setTextIsSelectable(true);
        row.addView(valueView);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
