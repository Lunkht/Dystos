package dev.dystos.app.presentation.exercise;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import dev.dystos.app.R;
import dev.dystos.app.databinding.FragmentExerciseEditorBinding;
import dev.dystos.app.di.ServiceLocator;
import dev.dystos.app.domain.model.CheckResult;
import dev.dystos.app.domain.model.Exercise;
import dev.dystos.app.domain.model.Lesson;
import dev.dystos.app.domain.usecase.CheckExercise;
import dev.dystos.app.ide.LanguageRegistry;
import dev.dystos.app.presentation.BaseFragment;
import dev.dystos.app.presentation.lesson.MarkdownRenderer;

/**
 * Éditeur d'exercice : l'apprenant écrit, exécute, compare.
 *
 * La vérification est locale. Les indices sont révélés un par un, dans
 * l'ordre : en donner trois d'un coup n'aide pas àProgresser.
 */
public final class ExerciseEditorFragment extends BaseFragment {

    private static final String ARG_LESSON_ID = "lecon_id";

    private FragmentExerciseEditorBinding binding;
    private Exercise exercise;
    private int hintIndex;
    private boolean solved;

    public static ExerciseEditorFragment newInstance(String lessonId) {
        ExerciseEditorFragment fragment = new ExerciseEditorFragment();
        Bundle args = new Bundle();
        args.putString(ARG_LESSON_ID, lessonId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected int layoutId() {
        return R.layout.fragment_exercise_editor;
    }

    /** Langage choisi dans les paramètres de l'IDE. */
    private dev.dystos.app.ide.Language ideLanguage() {
        String id = dev.dystos.app.core.IdePreferences.languageId(requireContext());
        return LanguageRegistry.exists(id)
                ? LanguageRegistry.get(id) : LanguageRegistry.defaultLanguage();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentExerciseEditorBinding.bind(view);

        String lessonId = requireArguments().getString(ARG_LESSON_ID, "");
        Lesson lesson = ServiceLocator.getLesson().execute(lessonId);

        if (lesson == null || lesson.getExercise() == null) {
            goBack();
            return;
        }

        exercise = lesson.getExercise();
        solved = ServiceLocator.getProgress().execute()
                .isExerciseSolved(lessonId);

        binding.editorTitle.setText(R.string.exercise_title);
        binding.exerciseInstruction.setText(
                MarkdownRenderer.render(requireContext(), exercise.getInstruction()));

        binding.codeInput.setLineNumbers(binding.lineNumbers);
        binding.codeInput.setLineNumberColor(lineNumberColour());
        binding.codeInput.applyIdePreferences();
        binding.symbolBar.bind(ideLanguage(), binding.codeInput);
        binding.codeInput.setLanguage(ideLanguage());

        binding.codeInput.setText(exercise.getInitialCode());
        binding.codeInput.setSelection(binding.codeInput.getText().length());
        showLiveError();

        binding.codeInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                showLiveError();
            }
        });

        binding.backButton.setOnClickListener(ignored -> goBack());
        binding.resetButton.setOnClickListener(ignored -> reset());
        binding.hintButton.setOnClickListener(ignored -> showNextHint());
        binding.solutionButton.setOnClickListener(ignored -> confirmSolution());
        binding.runButton.setOnClickListener(ignored -> run());
        binding.shareButton.setOnClickListener(ignored -> shareCode());

        if (solved) {
            binding.codeInput.setEnabled(false);
            binding.runButton.setEnabled(false);
            binding.hintButton.setEnabled(false);
            binding.solutionButton.setEnabled(false);
        }
    }

    /**
     * Affiche l'erreur d'analyse sous l'éditeur, sans exécuter.
     *
     * L'apprenant voit le problème pendant qu'il écrit plutôt qu'après avoir
     * cliqué sur « Vérifier ».
     */
    private void showLiveError() {
        dev.dystos.app.language.CodeAnalysis.Problem problem =
                binding.codeInput.getProblem();

        if (problem == null || binding.codeInput.getText().length() == 0) {
            binding.liveError.setVisibility(View.GONE);
            return;
        }

        binding.liveError.setVisibility(View.VISIBLE);
        binding.liveError.setText(getString(R.string.error_at_line,
                problem.line(), problem.message()));
    }

    private int lineNumberColour() {
        android.util.TypedValue value = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorOutline, value, true);
        return value.data;
    }

    private void goBack() {
        if (!isAdded()) {
            return;
        }
        getParentFragmentManager().popBackStack();
    }

    private void reset() {
        binding.codeInput.setText(exercise.getInitialCode());
        binding.codeInput.setSelection(binding.codeInput.getText().length());
        binding.resultCard.setVisibility(View.GONE);
        binding.hintCard.setVisibility(View.GONE);
        hintIndex = 0;
    }

    private void showNextHint() {
        if (exercise.getHints().isEmpty()) {
            binding.hintCard.setVisibility(View.VISIBLE);
            binding.hintText.setText(R.string.no_hint_left);
            binding.hintButton.setEnabled(false);
            return;
        }
        if (hintIndex >= exercise.getHints().size()) {
            binding.hintText.setText(R.string.no_hint_left);
            return;
        }
        binding.hintCard.setVisibility(View.VISIBLE);
        binding.hintText.setText(MarkdownRenderer.render(requireContext(),
                exercise.getHints().get(hintIndex)));
        hintIndex++;
        if (hintIndex >= exercise.getHints().size()) {
            binding.hintButton.setEnabled(false);
        }
    }

    /** La solution ne s'affiche qu'après une confirmation explicite. */
    private void confirmSolution() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.solution_confirm_title)
                .setMessage(R.string.solution_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    binding.hintCard.setVisibility(View.VISIBLE);
                    binding.hintText.setText(MarkdownRenderer.render(requireContext(),
                            exercise.getSolution()));
                })
                .show();
    }

    /**
     * Partage du code courant.
     *
     * Deux cibles : le texte brut pour un copier-coller, le fichier `.dys` pour
     * un envoi à quelqu'un qui a Dystos. Aucune donnée ne quitte l'appareil
     * par nous-mêmes : c'est l-sharing sheet du système qui s'en charge.
     */
    private void shareCode() {
        String code = binding.codeInput.getText().toString();
        String title = getString(R.string.share_subject);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.share_title)
                .setItems(new CharSequence[]{
                        getString(R.string.share_as_text),
                        getString(R.string.share_as_file)}, (dialog, which) -> {
                    if (which == 0) {
                        shareText(code, title);
                    } else {
                        shareFile(code, title);
                    }
                })
                .show();
    }

    private void shareText(String code, String title) {
        ShareCode.text(requireContext(), code, title);
    }

    private void shareFile(String code, String title) {
        ShareCode.file(requireContext(), code, title);
    }

    private void run() {
        String code = binding.codeInput.getText().toString();
        CheckExercise useCase = ServiceLocator.checkExercise();
        CheckResult result = useCase.execute(exercise, code,
                requireArguments().getString(ARG_LESSON_ID, ""));

        binding.resultCard.setVisibility(View.VISIBLE);
        binding.outputText.setVisibility(
                result.actualOutput().isEmpty() ? View.GONE : View.VISIBLE);
        binding.outputText.setText(result.actualOutput());

        switch (result.kind()) {
            case SUCCESS:
                showSuccess(result);
                break;
            case WRONG_OUTPUT:
                showWrongOutput(result);
                break;
            case TIMEOUT:
                showError(R.string.result_timeout, result.message(), result.line());
                break;
            case COMPILE_ERROR:
            case RUNTIME_ERROR:
            default:
                showError(R.string.result_runtime_error, result.message(), result.line());
                break;
        }
    }

    private void showSuccess(CheckResult result) {
        solved = true;
        binding.resultTitle.setText(R.string.result_success);
        binding.resultTitle.setTextColor(resolveColour(
                com.google.android.material.R.attr.colorPrimary));
        binding.resultMessage.setVisibility(View.GONE);
        binding.resultCard.setStrokeColor(resolveColour(
                com.google.android.material.R.attr.colorPrimary));

        binding.codeInput.setEnabled(false);
        binding.runButton.setEnabled(false);
        binding.hintButton.setEnabled(false);
        binding.solutionButton.setEnabled(false);
    }

    private void showWrongOutput(CheckResult result) {
        binding.resultTitle.setText(R.string.result_wrong_output);
        binding.resultTitle.setTextColor(resolveColour(
                com.google.android.material.R.attr.colorError));
        binding.resultCard.setStrokeColor(resolveColour(
                com.google.android.material.R.attr.colorError));
        binding.resultMessage.setVisibility(View.VISIBLE);
        binding.resultMessage.setText(getString(R.string.expected_output_block,
                result.expectedOutput()));
    }

    private void showError(int titleRes, String message, int line) {
        binding.resultTitle.setText(titleRes);
        binding.resultTitle.setTextColor(resolveColour(
                com.google.android.material.R.attr.colorError));
        binding.resultCard.setStrokeColor(resolveColour(
                com.google.android.material.R.attr.colorError));
        binding.resultMessage.setVisibility(View.VISIBLE);
        binding.resultMessage.setText(line > 0
                ? getString(R.string.error_at_line, line, message)
                : getString(R.string.error_no_line, message));
    }

    private int resolveColour(int attribute) {
        android.util.TypedValue value = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(attribute, value, true);
        return value.data;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
