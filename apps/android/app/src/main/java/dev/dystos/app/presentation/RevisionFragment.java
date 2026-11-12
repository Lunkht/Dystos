package dev.dystos.app.presentation;

import android.os.Bundle;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.dystos.app.R;
import dev.dystos.app.databinding.FragmentRevisionBinding;
import dev.dystos.app.di.ServiceLocator;
import dev.dystos.app.domain.model.Progress;
import dev.dystos.app.domain.model.Quiz;
import dev.dystos.app.presentation.chapter.ChapterAdapter;
import dev.dystos.app.presentation.chapter.ChapterListViewModel;
import dev.dystos.app.presentation.editor.SectionListAdapter;

/**
 * Onglet Révisions : progression globale (leçons lues, complétion, chapitres)
 * et outils de révision (quiz, référence, recherche dans les leçons).
 */
public final class RevisionFragment extends BaseFragment
        implements ChapterAdapter.OnChapterSelected {

    private FragmentRevisionBinding binding;
    private ChapterAdapter chapterAdapter;

    @Override
    protected int layoutId() {
        return R.layout.fragment_revision;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentRevisionBinding.bind(view);

        view.findViewById(R.id.back_button).setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            } else {
                ((MainActivity) requireActivity()).goHome();
            }
        });

        // Progression : liste des chapitres
        chapterAdapter = new ChapterAdapter(this);
        binding.chapterList.setAdapter(chapterAdapter);
        binding.chapterList.setLayoutManager(
                new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));

        // Révision : liste des quiz avec les scores
        List<Quiz> quizzes = ServiceLocator.getQuizzes().all();
        Map<String, Integer> scores = ServiceLocator.recordQuizAnswer().all();

        final List<Quiz> quizList = new ArrayList<>(quizzes);

        List<SectionListAdapter.Row> rows = new ArrayList<>();
        for (Quiz quiz : quizzes) {
            Integer score = scores.get(quiz.getId());
            rows.add(SectionListAdapter.Row.item(
                    quiz.getTitle(),
                    getString(R.string.quiz_best_score,
                            score == null ? 0 : score, 20),
                    getString(R.string.quiz_question,
                            1, quiz.getQuestions().size())));
        }
        if (rows.isEmpty()) {
            rows.add(SectionListAdapter.Row.item(getString(R.string.quiz_empty), "", ""));
        }

        SectionListAdapter adapter = new SectionListAdapter(position -> {
            if (position >= 0 && position < quizList.size()) {
                ((MainActivity) requireActivity()).openQuiz(quizList.get(position).getId());
            }
        });
        adapter.submit(rows);
        binding.quizList.setAdapter(adapter);
        binding.quizList.setLayoutManager(
                new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));

        MainActivity activity = (MainActivity) requireActivity();
        binding.referenceButton.setOnClickListener(ignored -> activity.openReference());
        binding.searchButton.setOnClickListener(ignored -> activity.openSearch());
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

        chapterAdapter.submit(state.getItems());

        int read = state.getReadLessons();
        int total = state.getTotalLessons();
        int percent = Math.round(state.getRatio() * 100f);

        binding.progressLabel.setText(
                getString(R.string.chapter_progress, read, total));
        binding.progressBar.setProgress(percent);
        binding.statLessonsRead.setText(String.valueOf(read));
        binding.statCompletion.setText(percent + "%");
    }

    @Override
    public void onChapterSelected(ChapterListViewModel.ChapterItem item) {
        if (item.getLessons().isEmpty()) return;
        Progress progress = ServiceLocator.getProgress().execute();
        for (dev.dystos.app.domain.model.Lesson lesson : item.getLessons()) {
            if (!progress.isLessonRead(lesson.getId())) {
                ((MainActivity) requireActivity()).openLesson(lesson.getId());
                return;
            }
        }
        ((MainActivity) requireActivity()).openLesson(item.getLessons().get(0).getId());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) {
            binding.chapterList.setAdapter(null);
            binding.quizList.setAdapter(null);
        }
        binding = null;
    }
}