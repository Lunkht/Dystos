package dev.dystos.app.presentation.lesson;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import dev.dystos.app.R;
import dev.dystos.app.databinding.FragmentLessonReaderBinding;
import dev.dystos.app.di.ServiceLocator;
import dev.dystos.app.domain.model.Example;
import dev.dystos.app.domain.model.Exercise;
import dev.dystos.app.domain.model.Lesson;
import dev.dystos.app.presentation.BaseFragment;
import dev.dystos.app.presentation.MainActivity;

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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentLessonReaderBinding.bind(view);
        binding.exerciseButton.setVisibility(View.GONE);

        View back = view.findViewById(R.id.back_button);
        if (back != null) {
            back.setOnClickListener(ignored -> {
                if (isAdded()) {
                    getParentFragmentManager().popBackStack();
                }
            });
        }

        String lessonId = requireArguments().getString(ARG_LESSON_ID, "");
        Lesson lesson = ServiceLocator.getLesson().execute(lessonId);

        if (lesson == null) {
            binding.lessonTitle.setText(R.string.lesson_missing);
            reveal(binding.lessonTitle, 0);
            return;
        }

        binding.lessonTitle.setText(lesson.getTitle());
        binding.lessonSummary.setText(lesson.getSummary());

        // Rendu dynamique du contenu (paragraphes texte et cartes de code avec option de copie)
        LinearLayout container = binding.lessonContentContainer;
        container.removeAllViews();

        String body = lesson.getBody();
        String[] parts = body.split("```");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part == null || part.trim().isEmpty()) continue;

            if (i % 2 == 1) {
                // Bloc de code ou de sortie
                View codeCard = getLayoutInflater().inflate(R.layout.code_item_card, container, false);
                String codeContent = part.trim();
                boolean isOutput = false;

                if (codeContent.startsWith("dys\n")) {
                    codeContent = codeContent.substring(4);
                } else if (codeContent.startsWith("dystos\n")) {
                    codeContent = codeContent.substring(7);
                } else if (codeContent.startsWith("python\n")) {
                    codeContent = codeContent.substring(7);
                } else {
                    // Pas de langage spécifié -> c'est un bloc de sortie / résultat !
                    isOutput = true;
                }

                if (isOutput) {
                    TextView title = codeCard.findViewById(R.id.code_header_title);
                    if (title != null) title.setText("Sortie");
                    View copyContainer = codeCard.findViewById(R.id.copy_container);
                    if (copyContainer != null) copyContainer.setVisibility(View.GONE);
                    TextView tvCode = codeCard.findViewById(R.id.code_content);
                    if (tvCode != null) {
                        tvCode.setText(codeContent);
                        tvCode.setTextColor(0xFF4DD4C0);
                        tvCode.setTextIsSelectable(true);
                    }
                } else {
                    setupCodeCard(codeCard, codeContent);
                }
                container.addView(codeCard);
            } else {
                // Paragraphe de texte
                TextView tv = new TextView(requireContext());
                CharSequence rendered = MarkdownRenderer.render(requireContext(), part);
                android.text.SpannableStringBuilder compact =
                        new android.text.SpannableStringBuilder(rendered);
                while (compact.length() > 0 && compact.charAt(compact.length() - 1) == '\n') {
                    compact.delete(compact.length() - 1, compact.length());
                }
                tv.setText(compact);
                tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
                tv.setTextColor(colour(com.google.android.material.R.attr.colorOnSurface));
                tv.setTextIsSelectable(true);
                tv.setLineSpacing(0, 1.25f);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                boolean followedByOutput = i + 1 < parts.length
                        && ((i + 1) % 2 == 1)
                        && isOutputBlock(parts[i + 1]);
                params.bottomMargin = followedByOutput ? 0 : dp(12);
                container.addView(tv, params);
            }
        }

        // Charger l'exemple associé si présent
        String exampleId = lesson.getExampleId();
        if (exampleId == null || exampleId.isEmpty()) {
            binding.exampleCard.setVisibility(View.GONE);
        } else {
            Example example = ServiceLocator.contentRepository().getExample(exampleId);
            if (example != null) {
                binding.exampleCard.setVisibility(View.VISIBLE);
                setupCodeCard(view.findViewById(R.id.example_code_card), example.getCode());
                String output = example.isVerifiable() ? example.getExpectedOutput() : example.getNote();
                View outContainer = view.findViewById(R.id.example_output_container);
                TextView outText = view.findViewById(R.id.example_output_text);
                if (outText != null && outContainer != null) {
                    outText.setText(output);
                    outContainer.setVisibility(output.isEmpty() ? View.GONE : View.VISIBLE);
                }
            } else {
                binding.exampleCard.setVisibility(View.GONE);
            }
        }

        Exercise exercise = lesson.getExercise();
        if (exercise == null) {
            binding.exerciseCard.setVisibility(View.GONE);
        } else {
            binding.exerciseCard.setVisibility(View.VISIBLE);
            binding.exerciseInstruction.setText(
                    MarkdownRenderer.render(requireContext(), exercise.getInstruction()));
            setupCodeCard(view.findViewById(R.id.exercise_code_card), exercise.getInitialCode());
            String expected = exercise.isVerifiable() ? exercise.getExpectedOutput() : "";
            View expContainer = view.findViewById(R.id.exercise_output_container);
            TextView expText = view.findViewById(R.id.exercise_output_text);
            if (expText != null && expContainer != null) {
                expText.setText(expected);
                expContainer.setVisibility(expected.isEmpty() ? View.GONE : View.VISIBLE);
            }
            binding.exerciseButton.setVisibility(View.VISIBLE);
            binding.exerciseButton.setOnClickListener(ignored ->
                    ((MainActivity) requireActivity()).openExercise(lessonId));
        }

        // Entrée en cascade : titre, résumé, corps, puis cartes d'exemple et d'exercice.
        reveal(binding.lessonTitle, 0);
        reveal(binding.lessonSummary, 60);
        reveal(binding.lessonContentContainer, 120);
        if (exampleId != null && !exampleId.isEmpty()) {
            reveal(binding.exampleCard, 180);
        }
        if (exercise != null) {
            reveal(binding.exerciseCard, 240);
        }

        ServiceLocator.markLessonRead().execute(lessonId);
    }

    private void setupCodeCard(View cardView, String codeText) {
        if (cardView == null) return;
        TextView tvCode = cardView.findViewById(R.id.code_content);
        View copyContainer = cardView.findViewById(R.id.copy_container);

        if (tvCode != null) {
            CharSequence highlighted = MarkdownRenderer.render(requireContext(), "```\n" + codeText + "\n```");
            tvCode.setText(highlighted);
            tvCode.setTextIsSelectable(true);
        }
        if (copyContainer != null) {
            copyContainer.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Code Dystos", codeText);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(requireContext(), "Code copié dans le presse-papier", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private boolean isOutputBlock(String block) {
        String content = block.trim();
        return !content.startsWith("dys\n")
                && !content.startsWith("dystos\n")
                && !content.startsWith("python\n");
    }

    /** Fait apparaître une vue en montant légèrement vers le haut. */
    private void reveal(View view, long delay) {
        view.setAlpha(0f);
        view.setTranslationY(dp(16));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(280)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int colour(int attribute) {
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
