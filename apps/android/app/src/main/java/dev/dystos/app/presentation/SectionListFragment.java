package dev.dystos.app.presentation;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import dev.dystos.app.R;
import dev.dystos.app.presentation.editor.SectionListAdapter;

import java.util.List;

/**
 * Écran de catalogue : liste de sections, réutilisée par les exemples, la
 * recherche, la référence et les quiz.
 *
 * Chaque écran ne fournit que ses lignes et son comportement de clic, ce qui
 * évite quatre listes quasi identiques.
 */
public abstract class SectionListFragment extends BaseFragment {

    private SectionListAdapter adapter;
    private RecyclerView list;
    private TextView titleView;

    @Override
    protected int layoutId() {
        return R.layout.fragment_section_list;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View back = view.findViewById(R.id.back_button);
        if (back != null) {
            back.setOnClickListener(ignored -> {
                if (isAdded()) {
                    getParentFragmentManager().popBackStack();
                }
            });
        }

        titleView = view.findViewById(R.id.screen_title);
        titleView.setText(titleText());

        list = view.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SectionListAdapter(this::onRowClicked);
        list.setAdapter(adapter);

        onListReady();
    }

    /** Titre affiché dans l'en-tête. */
    protected int titleText() {
        return R.string.app_name;
    }

    /** Titre déjà résolu, à utiliser depuis un sous-écran. */
    protected void setTitle(CharSequence text) {
        if (titleView != null) {
            titleView.setText(text);
        }
    }

    /** Construit les lignes une fois la liste branchée. */
    protected abstract void onListReady();

    /** Remplace le contenu de la liste. */
    protected void setRows(List<SectionListAdapter.Row> rows) {
        if (adapter != null) {
            adapter.submit(rows);
        }
    }

    /** Appelé quand l'utilisateur touche une ligne. */
    protected abstract void onRowClicked(int position);

    protected RecyclerView listView() {
        return list;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        adapter = null;
        list = null;
        titleView = null;
    }
}