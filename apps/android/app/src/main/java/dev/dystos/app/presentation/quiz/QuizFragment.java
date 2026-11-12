package dev.dystos.app.presentation.quiz;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;

import dev.dystos.app.R;
import dev.dystos.app.databinding.FragmentQuizBinding;
import dev.dystos.app.di.ServiceLocator;
import dev.dystos.app.domain.model.Quiz;
import dev.dystos.app.domain.usecase.RecordQuizAnswer;
import dev.dystos.app.presentation.BaseFragment;

/**
 * Un quiz de révision, une question à la fois.
 */
public final class QuizFragment extends BaseFragment {

    private static final String ARG_QUIZ_ID = "quiz_id";

    private FragmentQuizBinding binding;
    private Quiz quiz;
    private int index;
    private int correct;
    private int lives = 5;
    private boolean answered;
    private int selectedIndex = -1;

    public static QuizFragment newInstance(String quizId) {
        QuizFragment fragment = new QuizFragment();
        Bundle args = new Bundle();
        args.putString(ARG_QUIZ_ID, quizId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected int layoutId() {
        return R.layout.fragment_quiz;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentQuizBinding.bind(view);

        quiz = ServiceLocator.getQuizzes()
                .one(requireArguments().getString(ARG_QUIZ_ID, ""));
        if (quiz == null || quiz.isEmpty()) {
            goBack();
            return;
        }

        binding.backButton.setOnClickListener(ignored -> goBack());
        binding.nextButton.setOnClickListener(ignored -> {
            if (!answered) {
                if (selectedIndex >= 0) {
                    answer(selectedIndex);
                }
            } else {
                advance();
            }
        });

        showQuestion();
    }

    private void goBack() {
        if (isAdded()) {
            getParentFragmentManager().popBackStack();
        }
    }

    private void showQuestion() {
        Quiz.Question question = quiz.getQuestions().get(index);
        answered = false;
        selectedIndex = -1;

        binding.questionCounter.setText(getString(R.string.quiz_question,
                index + 1, quiz.getQuestions().size()));
        binding.questionPrompt.setText(question.getPrompt());
        popIn(binding.questionCounter, 0);
        popIn(binding.questionPrompt, 40);

        LinearProgressIndicator progress = binding.progress;
        progress.setMax(quiz.getQuestions().size());
        progress.setProgress(index + 1);

        binding.livesText.setText("♥ " + lives);

        binding.feedbackCard.setVisibility(View.GONE);
        binding.nextButton.setEnabled(false);
        binding.nextButton.setText(R.string.verify);

        LinearLayout container = binding.choicesContainer;
        container.removeAllViews();
        List<String> choices = question.getChoices();

        for (int i = 0; i < choices.size(); i++) {
            final int choiceIndex = i;
            View choiceView = LayoutInflater.from(requireContext()).inflate(R.layout.item_quiz_choice, container, false);
            TextView tvChoice = choiceView.findViewById(R.id.choice_text);
            tvChoice.setText(choices.get(i));

            choiceView.setOnClickListener(v -> {
                if (answered) return;
                selectedIndex = choiceIndex;
                highlightSelectedChoice(container, choiceIndex);
                binding.nextButton.setEnabled(true);
            });

            container.addView(choiceView);
            popIn(choiceView, 100L + i * 40L);
        }
    }

    private void highlightSelectedChoice(LinearLayout container, int selectedIdx) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View choice = container.getChildAt(i);
            if (i == selectedIdx) {
                choice.setBackgroundColor(colour(com.google.android.material.R.attr.colorSurfaceVariant));
            } else {
                choice.setBackgroundResource(R.drawable.card_gradient_bg);
            }
        }
    }

    private void answer(int selected) {
        Quiz.Question question = quiz.getQuestions().get(index);
        answered = true;
        boolean good = selected == question.getCorrectIndex();
        if (good) {
            correct++;
        } else {
            lives = Math.max(0, lives - 1);
            binding.livesText.setText("♥ " + lives);
        }

        LinearLayout container = binding.choicesContainer;
        for (int i = 0; i < container.getChildCount(); i++) {
            View choice = container.getChildAt(i);
            choice.setEnabled(false);
            if (i == question.getCorrectIndex()) {
                choice.setBackgroundColor(0x224DD4C0);
            } else if (i == selected) {
                choice.setBackgroundColor(0x22FF5252);
            }
        }

        // Afficher dialog_quiz_result sous forme de BottomSheetDialog lors de la réponse
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_quiz_result, null);
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        bottomSheetDialog.setContentView(dialogView);
        bottomSheetDialog.setCancelable(false);

        View tvCheckIcon = dialogView.findViewById(R.id.tvCheckIcon);
        View dysBot = dialogView.findViewById(R.id.dysBot);
        animateDialogIcons(tvCheckIcon, dysBot);

        TextView tvTitle = dialogView.findViewById(R.id.tvResultTitle);
        TextView tvSub = dialogView.findViewById(R.id.tvCharacterName);
        TextView tvExplanation = dialogView.findViewById(R.id.tvExplanation);
        View btnNext = dialogView.findViewById(R.id.btnNext);

        tvTitle.setText(good ? "Super !" : "Incorrect");
        tvSub.setText(good ? "Bonne réponse" : "La bonne réponse était :");
        String explanationText = question.getExplanation().isEmpty()
                ? (good ? getString(R.string.quiz_correct) : question.getChoices().get(question.getCorrectIndex()))
                : question.getExplanation();
        tvExplanation.setText(explanationText);

        if (btnNext instanceof TextView) {
            ((TextView) btnNext).setText(index == quiz.getQuestions().size() - 1 ? "TERMINER" : "SUIVANTE");
        }

        btnNext.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            advance();
        });

        bottomSheetDialog.show();

        binding.nextButton.setEnabled(true);
        binding.nextButton.setText(index == quiz.getQuestions().size() - 1
                ? R.string.quiz_finish : R.string.quiz_next);
    }

    private void advance() {
        if (index + 1 < quiz.getQuestions().size()) {
            index++;
            showQuestion();
            return;
        }
        finish();
    }

    private void finish() {
        Quiz.Result result = new Quiz.Result(correct, quiz.getQuestions().size());
        RecordQuizAnswer recorder = ServiceLocator.recordQuizAnswer();
        int stored = recorder.record(quiz.getId(), result);

        binding.progress.setProgress(quiz.getQuestions().size());
        binding.quizScrollView.setVisibility(View.GONE);
        binding.bottomContainer.setVisibility(View.GONE);
        binding.resultContainer.setVisibility(View.VISIBLE);

        boolean passed = result.isPassed();
        int resultColor = colour(passed
                ? com.google.android.material.R.attr.colorPrimary
                : com.google.android.material.R.attr.colorError);
        int percent = Math.round(result.getCorrect() * 100f / result.getTotal());

        binding.resultStatus.setText(passed
                ? R.string.quiz_result_passed
                : R.string.quiz_result_failed);
        binding.resultStatus.setTextColor(resultColor);

        binding.resultScoreFraction.setText(
                result.getCorrect() + " / " + result.getTotal());
        binding.resultScoreFraction.setTextColor(resultColor);

        binding.resultPercent.setText(percent + " %");
        binding.resultCorrect.setText(result.getCorrect() + " / " + result.getTotal());
        binding.resultSaved.setText(stored + " / 20");
        binding.resultMessage.setText(passed
                ? R.string.quiz_result_success
                : R.string.quiz_result_retry_hint);

        binding.btnFinishQuiz.setOnClickListener(v -> goBack());
        binding.btnRetryQuiz.setOnClickListener(v -> restart());
        popIn(binding.resultContainer, 60);
    }

    /** Relance le quiz depuis la première question. */
    private void restart() {
        index = 0;
        correct = 0;
        lives = 5;
        answered = false;
        selectedIndex = -1;

        binding.resultContainer.setVisibility(View.GONE);
        binding.quizScrollView.setVisibility(View.VISIBLE);
        binding.bottomContainer.setVisibility(View.VISIBLE);
        showQuestion();
    }

    private void animateDialogIcons(View tvCheckIcon, View dysBot) {
        if (tvCheckIcon == null || dysBot == null) return;
        dysBot.setVisibility(View.GONE);
        tvCheckIcon.setVisibility(View.VISIBLE);
        tvCheckIcon.setScaleX(0f);
        tvCheckIcon.setScaleY(0f);
        tvCheckIcon.animate().scaleX(1f).scaleY(1f).setDuration(400).start();

        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable toggleTask = new Runnable() {
            int switchCount = 0;
            boolean showCheck = true;
            @Override
            public void run() {
                if (tvCheckIcon.getWindowToken() == null) return;
                if (switchCount >= 2) return;
                switchCount++;
                showCheck = !showCheck;
                View visible = showCheck ? tvCheckIcon : dysBot;
                View hidden = showCheck ? dysBot : tvCheckIcon;

                hidden.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(300).withEndAction(() -> {
                    hidden.setVisibility(View.GONE);
                    visible.setVisibility(View.VISIBLE);
                    visible.setAlpha(0f);
                    visible.setScaleX(0.5f);
                    visible.setScaleY(0.5f);
                    visible.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).start();
                }).start();

                if (switchCount < 2) {
                    handler.postDelayed(this, 1600);
                }
            }
        };
        handler.postDelayed(toggleTask, 1600);
    }

    private void popIn(View view, long delay) {
        view.setAlpha(0f);
        view.setTranslationY(dp(12));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(240)
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
