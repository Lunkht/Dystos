package dev.dystos.app.presentation.chapter;

import java.util.List;

import dev.dystos.app.R;
import dev.dystos.app.databinding.FragmentChapterListBinding;
import dev.dystos.app.di.ServiceLocator;
import dev.dystos.app.domain.model.Lesson;
import dev.dystos.app.domain.model.Progress;
import dev.dystos.app.presentation.BaseFragment;
import dev.dystos.app.presentation.MainActivity;

/** Écran d'accueil du parcours : liste des chapitres et progression globale. */
public final class ChapterListFragment extends BaseFragment
        implements ChapterAdapter.OnChapterSelected {

    private FragmentChapterListBinding binding;
    private ChapterAdapter adapter;

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

        adapter = new ChapterAdapter(this);
        binding.chapterList.setAdapter(adapter);
        binding.chapterList.setLayoutManager(
                new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));

        binding.themeToggle.setOnClickListener(
                ignored -> ((MainActivity) requireActivity()).toggleTheme());
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
        adapter.submit(state.getItems());
        binding.progressLabel.setText(getString(
                R.string.chapter_progress, state.getReadLessons(), state.getTotalLessons()));
        binding.progressBar.setProgress(Math.round(state.getRatio() * 100f));
    }

    private ChapterListViewModel newViewModel() {
        return new ChapterListViewModel(
                ServiceLocator.getChapter(),
                ServiceLocator.getProgress(),
                ServiceLocator.contentRepository());
    }

    @Override
    public void onChapterSelected(ChapterListViewModel.ChapterItem item) {
        openLesson(item.getLessons());
    }

    /** Reprendre là où l'utilisateur s'est arrêté dans le chapitre. */
    private void openLesson(List<Lesson> lessons) {
        if (lessons.isEmpty()) {
            return;
        }
        Progress progress = ServiceLocator.getProgress().execute();
        for (Lesson lesson : lessons) {
            if (!progress.isLessonRead(lesson.getId())) {
                ((MainActivity) requireActivity()).openLesson(lesson.getId());
                return;
            }
        }
        ((MainActivity) requireActivity()).openLesson(lessons.get(0).getId());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.chapterList.setAdapter(null);
        binding = null;
    }
}