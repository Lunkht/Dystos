package dev.dystos.app.presentation.quiz;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.dystos.app.R;
import dev.dystos.app.di.ServiceLocator;
import dev.dystos.app.domain.model.Quiz;
import dev.dystos.app.domain.usecase.RecordQuizAnswer;
import dev.dystos.app.presentation.MainActivity;
import dev.dystos.app.presentation.SectionListFragment;
import dev.dystos.app.presentation.editor.SectionListAdapter;

/**
 * Catalogue des quiz de révision.
 *
 * Le score déjà obtenu est affiché avec chaque quiz : c'est ce qui donne envie
 * de recommencer, et cela évite de rouvrir un quiz pour vérifier ce qu'on avait
 * fait la dernière fois.
 */
public final class QuizListFragment extends SectionListFragment {

    private final List<Quiz> quizzes = new ArrayList<>();

    @Override
    protected int titleText() {
        return R.string.section_quizzes;
    }

    @Override
    protected void onListReady() {
        List<Quiz> all = ServiceLocator.getQuizzes().all();
        Map<String, Integer> scores = ServiceLocator.recordQuizAnswer().all();

        List<SectionListAdapter.Row> rows = new ArrayList<>();
        for (Quiz quiz : all) {
            quizzes.add(quiz);

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
        setRows(rows);
    }

    @Override
    protected void onRowClicked(int position) {
        if (position < 0 || position >= quizzes.size()) {
            return;
        }
        ((MainActivity) requireActivity()).openQuiz(quizzes.get(position).getId());
    }
}