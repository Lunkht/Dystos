package dev.dystos.app.presentation.lesson;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import dev.dystos.app.R;
import dev.dystos.app.databinding.FragmentLessonReaderBinding;
import dev.dystos.app.di.ServiceLocator;
import dev.dystos.app.domain.model.Exercise;
import dev.dystos.app.domain.model.Lesson;
import dev.dystos.app.presentation.BaseFragment;

/**
 * Lecteur de leçon hors ligne.
 *
 * Le corps Markdown est rendu par un parseur local : pas de WebView, pas de
 * bibliothèque distante, pas de JavaScript.
 */
public final class LessonReaderFragment extends BaseFragment {

    private static final String ARG_LESSON_ID = "lecon_id";

    private FragmentLessonReaderBinding binding;

    public static LessonReaderFragment newInstance(String lessonId) {
        LessonReaderFragment fragment = new LessonReaderFragment();
        Bundle args = new Bundle();
        args.putString(ARG_LESSON_ID, lessonId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected int layoutId() {
        return R.layout.fragment_lesson_reader;
    }

    @Override
    public void onViewCreated(@NonNull android.view.View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentLessonReaderBinding.bind(view);

        String lessonId = requireArguments().getString(ARG_LESSON_ID, "");
        Lesson lesson = ServiceLocator.getLesson().execute(lessonId);

        if (lesson == null) {
            binding.lessonTitle.setText(R.string.lesson_missing);
            return;
        }

        binding.lessonTitle.setText(lesson.getTitle());
        binding.lessonSummary.setText(lesson.getSummary());
        binding.lessonBody.setText(MarkdownRenderer.render(lesson.getBody()));

        Exercise exercise = lesson.getExercise();
        if (exercise == null) {
            binding.exerciseCard.setVisibility(android.view.View.GONE);
        } else {
            binding.exerciseCard.setVisibility(android.view.View.VISIBLE);
            binding.exerciseInstruction.setText(exercise.getInstruction());
            binding.exerciseCode.setText(exercise.getInitialCode());
            binding.exerciseCode.setTextIsSelectable(true);
            binding.exerciseExpected.setText(
                    exercise.isVerifiable() ? exercise.getExpectedOutput() : "");
        }

        ServiceLocator.markLessonRead().execute(lessonId);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}