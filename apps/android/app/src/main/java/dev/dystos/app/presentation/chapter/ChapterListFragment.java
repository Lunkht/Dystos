package dev.dystos.app.presentation.chapter;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.List;

import dev.dystos.app.R;
import dev.dystos.app.core.ActivityTracker;
import dev.dystos.app.databinding.FragmentChapterListBinding;
import dev.dystos.app.di.ServiceLocator;
import dev.dystos.app.domain.model.Lesson;
import dev.dystos.app.domain.model.Progress;
import dev.dystos.app.presentation.BaseFragment;
import dev.dystos.app.presentation.MainActivity;

/** Accueil du parcours : progression et accès direct à la prochaine leçon. */
public final class ChapterListFragment extends BaseFragment {

    private FragmentChapterListBinding binding;
    private String nextLessonId;

    @Override
    protected int layoutId() {
        return R.layout.fragment_chapter_list;
    }

    @Override
    public void onViewCreated(
            android.view.View view,
            android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentChapterListBinding.bind(view);

        MainActivity activity = (MainActivity) requireActivity();
        binding.continueButton.setOnClickListener(ignored -> openNextLesson());
        binding.learningCard.setOnClickListener(ignored -> openNextLesson());
        binding.examplesButton.setOnClickListener(ignored -> activity.openExamples());
        binding.searchButton.setOnClickListener(ignored -> activity.openSearch());
        binding.quizzesButton.setOnClickListener(ignored -> activity.openQuizzes());
        binding.referenceButton.setOnClickListener(ignored -> activity.openReference());

        View userProfile = view.findViewById(R.id.userProle);
        if (userProfile != null) {
            userProfile.setOnClickListener(ignored -> activity.openProfile());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // La progression change quand une leçon est marquée lue : on relit
        // l'état à chaque retour sur cet écran.
        refresh();
    }

    private void refresh() {
        ChapterListViewModel.State state = newViewModel().load();

        int percent = Math.round(state.getRatio() * 100f);
        binding.progressLabel.setText(getString(
                R.string.chapter_progress, state.getReadLessons(), state.getTotalLessons()));
        binding.progressBar.setProgress(percent);
        binding.progressPercent.setText(percent + "%");
        bindNextLesson(state);

        refreshStreak();
    }

    private void bindNextLesson(ChapterListViewModel.State state) {
        Progress progress = ServiceLocator.getProgress().execute();
        nextLessonId = null;

        for (ChapterListViewModel.ChapterItem chapter : state.getItems()) {
            for (Lesson lesson : chapter.getLessons()) {
                if (!progress.isLessonRead(lesson.getId())) {
                    nextLessonId = lesson.getId();
                    binding.learningChapter.setText(chapter.getTitle());
                    binding.learningLesson.setText(lesson.getTitle());
                    binding.continueButton.setText(R.string.learning_continue);
                    binding.continueButton.setEnabled(true);
                    return;
                }
            }
        }

        if (!state.getItems().isEmpty() && !state.getItems().get(0).getLessons().isEmpty()) {
            Lesson firstLesson = state.getItems().get(0).getLessons().get(0);
            nextLessonId = firstLesson.getId();
            binding.learningChapter.setText(R.string.learning_all_complete);
            binding.learningLesson.setText(firstLesson.getTitle());
            binding.continueButton.setText(R.string.learning_review);
            binding.continueButton.setEnabled(true);
            return;
        }

        binding.learningChapter.setText(R.string.learning_unavailable);
        binding.learningLesson.setText("");
        binding.continueButton.setEnabled(false);
    }

    private void openNextLesson() {
        if (nextLessonId != null) {
            ((MainActivity) requireActivity()).openLesson(nextLessonId);
        }
    }

    private void refreshStreak() {
        binding.workoutCount.setText(String.valueOf(
                ActivityTracker.totalDays(requireContext())));
        binding.streakCount.setText(String.valueOf(
                ActivityTracker.streak(requireContext())));
        binding.maxStreakCount.setText(String.valueOf(
                ActivityTracker.bestStreak(requireContext())));

        ImageView[] cells = {
                binding.d1, binding.d2, binding.d3, binding.d4,
                binding.d5, binding.d6, binding.d7};
        TextView[] letters = {
                binding.mon, binding.tue, binding.wed, binding.thu,
                binding.fra, binding.sat, binding.sun};

        LocalDate today = LocalDate.now();
        List<LocalDate> week = ActivityTracker.currentWeek();
        for (int i = 0; i < week.size(); i++) {
            LocalDate day = week.get(i);
            cells[i].setVisibility(
                    ActivityTracker.isActive(requireContext(), day)
                            ? View.VISIBLE
                            : View.GONE);
            ((View) letters[i].getParent()).setBackgroundResource(
                    day.equals(today) ? R.drawable.day_bg_today : R.drawable.day_bg);
        }
    }

    private ChapterListViewModel newViewModel() {
        return new ChapterListViewModel(
                ServiceLocator.getChapter(),
                ServiceLocator.getProgress(),
                ServiceLocator.contentRepository());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}