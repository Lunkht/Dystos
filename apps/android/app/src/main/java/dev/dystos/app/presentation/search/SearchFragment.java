package dev.dystos.app.presentation.search;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import dev.dystos.app.R;
import dev.dystos.app.databinding.FragmentSearchBinding;
import dev.dystos.app.di.ServiceLocator;
import dev.dystos.app.domain.model.SearchHit;
import dev.dystos.app.domain.usecase.SearchLessons;
import dev.dystos.app.presentation.BaseFragment;
import dev.dystos.app.presentation.MainActivity;
import dev.dystos.app.presentation.editor.SectionListAdapter;

/**
 * Recherche dans les leçons.
 *
 * La requête est envoyée au clavier et à chaque modification : la base ne
 * contient que quelques leçons, l'index est déjà en mémoire, et attendre la
 * validation rend la recherche plus lente qu'elle ne paraît.
 */
public final class SearchFragment extends BaseFragment {

    /** Délai avant de relancer une recherche pendant la saisie. */
    private static final long TYPING_DELAY_MS = 200L;

    private FragmentSearchBinding binding;
    private SectionListAdapter adapter;
    private final List<SearchHit> hits = new ArrayList<>();
    private final Runnable search = this::runSearch;

    @Override
    protected int layoutId() {
        return R.layout.fragment_search;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentSearchBinding.bind(view);

        MaterialButton back = view.findViewById(R.id.back_button);
        back.setOnClickListener(ignored -> {
            if (isAdded()) {
                getParentFragmentManager().popBackStack();
            }
        });

        RecyclerView list = view.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SectionListAdapter(this::openHit);
        list.setAdapter(adapter);

        TextInputEditText input = view.findViewById(R.id.query_input);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // On regroupe les frappes : une requête FTS par caractère serait
                // du travail jeté.
                binding.queryInput.removeCallbacks(search);
                binding.queryInput.postDelayed(search, TYPING_DELAY_MS);
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });

        setEmpty(getString(R.string.search_prompt));
    }

    private void runSearch() {
        String query = binding.queryInput.getText() == null
                ? "" : binding.queryInput.getText().toString();

        if (query.trim().length() < 2) {
            hits.clear();
            adapter.submit(List.of());
            setEmpty(query.trim().isEmpty()
                    ? getString(R.string.search_prompt) : getString(R.string.search_empty));
            return;
        }

        List<SearchHit> results = ServiceLocator.searchLessons().search(query);

        List<SectionListAdapter.Row> rows = new ArrayList<>();
        hits.clear();
        for (SearchHit hit : results) {
            hits.add(hit);
            rows.add(SectionListAdapter.Row.item(
                    hit.getTitle(), hit.getChapter(),
                    hit.hasSnippet() ? hit.getSnippet() : hit.getSummary()));
        }
        adapter.submit(rows);

        if (results.isEmpty()) {
            setEmpty(getString(R.string.search_empty));
        } else {
            binding.emptyLabel.setVisibility(View.GONE);
        }
    }

    private void setEmpty(String message) {
        if (binding == null) {
            return;
        }
        binding.emptyLabel.setText(message);
        binding.emptyLabel.setVisibility(View.VISIBLE);
    }

    private void openHit(int position) {
        if (position < 0 || position >= hits.size()) {
            return;
        }
        ((MainActivity) requireActivity()).openLesson(hits.get(position).getLessonId());
    }

    @Override
    public void onDestroyView() {
        if (binding != null) {
            binding.queryInput.removeCallbacks(search);
        }
        super.onDestroyView();
        binding = null;
        adapter = null;
        hits.clear();
    }
}