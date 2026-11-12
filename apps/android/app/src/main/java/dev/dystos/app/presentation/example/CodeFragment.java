package dev.dystos.app.presentation.example;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import dev.dystos.app.R;
import dev.dystos.app.databinding.FragmentCodeBinding;
import dev.dystos.app.di.ServiceLocator;
import dev.dystos.app.domain.model.CheckResult;
import dev.dystos.app.domain.model.Example;
import dev.dystos.app.language.CodeAnalysis;
import dev.dystos.app.ide.LanguageRegistry;
import dev.dystos.app.presentation.BaseFragment;
import dev.dystos.app.presentation.exercise.ShareCode;

/**
 * Éditeur libre : ouvrir un exemple, l'exécuter, le partager.
 *
 * Contrairement à l'exercice, rien n'est mémorisé et le code reste modifiable
 * après une réussite : un exemple est une base de travail, pas une consigne.
 */
public final class CodeFragment extends BaseFragment {

    private static final String ARG_EXAMPLE_ID = "exemple_id";

    private FragmentCodeBinding binding;
    private Example example;

    public static CodeFragment newInstance(String exampleId) {
        CodeFragment fragment = new CodeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EXAMPLE_ID, exampleId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected int layoutId() {
        return R.layout.fragment_code;
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
        binding = FragmentCodeBinding.bind(view);

        String exampleId = requireArguments().getString(ARG_EXAMPLE_ID, "");
        example = ServiceLocator.getExamples().one(exampleId);

        if (example == null) {
            goBack();
            return;
        }

        binding.codeTitle.setText(example.getTitle());
        binding.codeCategory.setText(example.isNotExecutableYet()
                ? example.getNote() : example.getCategory());

        binding.codeInput.setLineNumbers(binding.lineNumbers);
        binding.codeInput.setLineNumberColor(lineNumberColour());
        binding.codeInput.applyIdePreferences();
        binding.symbolBar.bind(ideLanguage(), binding.codeInput);
        binding.codeInput.setLanguage(ideLanguage());
        binding.codeInput.setText(example.getCode());
        binding.codeInput.setSelection(binding.codeInput.getText().length());
        showLiveError();

        // Un exemple qui illustre une fonctionnalité absente du moteur ne peut
        // pas être exécuté ici. Désactiver le bouton et l'expliquer vaut mieux
        // qu'un échec de compilation qui n'apprend rien à l'utilisateur.
        binding.runButton.setEnabled(!example.isNotExecutableYet());
        binding.noticeLabel.setVisibility(
                example.isNotExecutableYet() ? View.VISIBLE : View.GONE);
        binding.noticeLabel.setText(example.getNote());

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
        binding.runButton.setOnClickListener(ignored -> run());
        binding.shareButton.setOnClickListener(ignored -> share());
    }

    private void goBack() {
        if (isAdded()) {
            getParentFragmentManager().popBackStack();
        }
    }

    private void reset() {
        binding.codeInput.setText(example.getCode());
        binding.codeInput.setSelection(binding.codeInput.getText().length());
        binding.resultCard.setVisibility(View.GONE);
    }

    private void showLiveError() {
        CodeAnalysis.Problem problem = binding.codeInput.getProblem();
        if (problem == null || binding.codeInput.getText().length() == 0) {
            binding.liveError.setVisibility(View.GONE);
            return;
        }
        binding.liveError.setVisibility(View.VISIBLE);
        binding.liveError.setText(getString(R.string.error_at_line,
                problem.line(), problem.message()));
    }

    private void run() {
        String code = binding.codeInput.getText().toString();

        if (!example.isVerifiable()) {
            // Sans sortie attendue, il n'y a rien à comparer : on affiche ce que
            // le programme produit, ce qui est déjà utile.
            CheckResult result = ServiceLocator.checkExercise().check(null, code);
            showOutput(result, null);
            return;
        }

        CheckResult result = ServiceLocator.checkExercise()
                .check(example.toExercise(), code);
        showOutput(result, example.getExpectedOutput());
    }

    private void showOutput(CheckResult result, String expected) {
        binding.resultCard.setVisibility(View.VISIBLE);
        binding.outputText.setVisibility(
                result.actualOutput().isEmpty() ? View.GONE : View.VISIBLE);
        binding.outputText.setText(result.actualOutput());
        binding.expectedText.setVisibility(
                expected == null ? View.GONE : View.VISIBLE);
        binding.expectedText.setText(expected == null ? "" : getString(
                R.string.expected_output_block, expected));

        int titleRes;
        int colour;

        switch (result.kind()) {
            case SUCCESS:
                titleRes = expected == null
                        ? R.string.result_ran : R.string.result_matches;
                colour = com.google.android.material.R.attr.colorPrimary;
                break;
            case WRONG_OUTPUT:
                titleRes = R.string.result_wrong_output;
                colour = com.google.android.material.R.attr.colorError;
                break;
            case TIMEOUT:
                titleRes = R.string.result_timeout;
                colour = com.google.android.material.R.attr.colorError;
                break;
            case COMPILE_ERROR:
                titleRes = R.string.result_compile_error;
                colour = com.google.android.material.R.attr.colorError;
                break;
            case RUNTIME_ERROR:
            default:
                titleRes = R.string.result_runtime_error;
                colour = com.google.android.material.R.attr.colorError;
                break;
        }

        if (result.message().isEmpty()) {
            binding.resultTitle.setText(titleRes);
        } else {
            binding.resultTitle.setText(getString(R.string.error_at_line,
                    result.line(), result.message()));
        }
        binding.resultTitle.setTextColor(resolveColour(colour));
        binding.resultCard.setStrokeColor(resolveColour(colour));
    }

    private void share() {
        String code = binding.codeInput.getText().toString();
        String title = getString(R.string.share_subject);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.share_title)
                .setItems(new CharSequence[]{
                        getString(R.string.share_as_text),
                        getString(R.string.share_as_file)}, (dialog, which) -> {
                    if (which == 0) {
                        ShareCode.text(requireContext(), code, title);
                    } else {
                        ShareCode.file(requireContext(), code, title);
                    }
                })
                .show();
    }

    private int lineNumberColour() {
        return resolveColour(com.google.android.material.R.attr.colorOutline);
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