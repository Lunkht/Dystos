package dev.dystos.app.presentation.reference;

import java.util.ArrayList;
import java.util.List;

import dev.dystos.app.R;
import dev.dystos.app.di.ServiceLocator;
import dev.dystos.app.domain.model.ReferenceEntry;
import dev.dystos.app.presentation.SectionListFragment;
import dev.dystos.app.presentation.editor.SectionListAdapter;

/**
 * Aide-mémoire du langage.
 *
 * Un seul écran, pas de navigation par section : l'utilisateur est en train
 * d'ecrire du code, il veut une ligne de reference, pas un parcours. Tout
 * reste donc dans une liste defilante.
 */
public final class ReferenceFragment extends SectionListFragment {

    @Override
    protected int titleText() {
        return R.string.section_reference;
    }

    @Override
    protected void onListReady() {
        List<ReferenceEntry.Section> sections = ServiceLocator.getReference().sections();

        List<SectionListAdapter.Row> rows = new ArrayList<>();
        for (ReferenceEntry.Section section : sections) {
            rows.add(SectionListAdapter.Row.header(section.getTitle()));
            if (!section.getIntro().isEmpty()) {
                rows.add(SectionListAdapter.Row.item(section.getIntro(), "", ""));
            }
            for (ReferenceEntry entry : section.getEntries()) {
                rows.add(SectionListAdapter.Row.item(
                        entry.getName(), entry.getDescription(), entry.getSignature()));
            }
        }

        if (rows.isEmpty()) {
            rows.add(SectionListAdapter.Row.item(getString(R.string.reference_empty), "", ""));
        }
        setRows(rows);
    }

    @Override
    protected void onRowClicked(int position) {
        // Une entrée de reference se copie, elle ne s'ouvre pas.
    }
}