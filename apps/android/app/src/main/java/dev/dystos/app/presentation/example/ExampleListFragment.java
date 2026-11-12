package dev.dystos.app.presentation.example;

import java.util.ArrayList;
import java.util.List;

import dev.dystos.app.R;
import dev.dystos.app.di.ServiceLocator;
import dev.dystos.app.domain.model.Example;
import dev.dystos.app.domain.usecase.GetExamples;
import dev.dystos.app.presentation.MainActivity;
import dev.dystos.app.presentation.SectionListFragment;
import dev.dystos.app.presentation.editor.SectionListAdapter;

/**
 * Catalogue des vingt programmes d'exemple.
 *
 * Regroupés par chapitre, parce que « quel exemple montre les fonctions ? » est
 * une question plus fréquente que « quel est l'exemple 7 ? ».
 */
public final class ExampleListFragment extends SectionListFragment {

    private final List<Example> examples = new ArrayList<>();

    @Override
    protected int titleText() {
        return R.string.section_examples;
    }

    @Override
    protected void onListReady() {
        GetExamples useCase = ServiceLocator.getExamples();
        List<GetExamples.Group> groups = useCase.grouped();

        List<SectionListAdapter.Row> rows = new ArrayList<>();
        for (GetExamples.Group group : groups) {
            rows.add(SectionListAdapter.Row.header(group.getCategory()));
            for (Example example : group.getExamples()) {
                examples.add(example);
                rows.add(SectionListAdapter.Row.item(
                        example.getTitle(),
                        example.isVerifiable()
                                ? getString(R.string.example_expected, truncate(
                                        example.getExpectedOutput()))
                                : getString(R.string.example_not_executable),
                        ""));
            }
        }

        if (rows.isEmpty()) {
            rows.add(SectionListAdapter.Row.item(
                    getString(R.string.examples_empty), "", ""));
        }
        setRows(rows);
    }

    @Override
    protected void onRowClicked(int position) {
        if (position < 0 || position >= examples.size()) {
            return;
        }
        ((MainActivity) requireActivity()).openExample(examples.get(position).getId());
    }

    /** L'extrait d'un exemple peut être long : on garde ce qui tient sur deux lignes. */
    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        String single = text.replace("\n", " · ");
        return single.length() <= 60 ? single : single.substring(0, 57) + "…";
    }
}