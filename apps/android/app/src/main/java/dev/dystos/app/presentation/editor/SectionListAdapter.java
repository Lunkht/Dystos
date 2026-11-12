package dev.dystos.app.presentation.editor;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import dev.dystos.app.R;
import dev.dystos.app.presentation.lesson.MarkdownRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Liste à plat, réutilisée par les écrans de catalogue.
 *
 * Elle affiche des en-têtes de section et des lignes cliquables. Réutiliser une
 * seule liste évite d'écrire quatre fois la même mécanique deRecyclerView.
 */
public final class SectionListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /** Deux types de ligne : un titre de section, ou une ligne cliquable. */
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    /** Une ligne de la liste. */
    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    private final List<Row> rows = new ArrayList<>();
    private final OnItemClickListener listener;

    public SectionListAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    /** Une ligne. */
    public static final class Row {
        private final int type;
        private final String primary;
        private final String secondary;
        private final String detail;

        private Row(int type, String primary, String secondary, String detail) {
            this.type = type;
            this.primary = primary == null ? "" : primary;
            this.secondary = secondary == null ? "" : secondary;
            this.detail = detail == null ? "" : detail;
        }

        public static Row header(String title) {
            return new Row(TYPE_HEADER, title, "", "");
        }

        public static Row item(String primary, String secondary, String detail) {
            return new Row(TYPE_ITEM, primary, secondary, detail);
        }

        public String getPrimary() {
            return primary;
        }

        public String getSecondary() {
            return secondary;
        }

        public String getDetail() {
            return detail;
        }
    }

    /** Remplace toutes les lignes. */
    public void submit(List<Row> newRows) {
        rows.clear();
        if (newRows != null) {
            rows.addAll(newRows);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(
                    R.layout.item_section_header, parent, false));
        }
        return new ItemHolder(inflater.inflate(R.layout.item_list_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);

        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).title.setText(row.getPrimary());
            return;
        }

        ItemHolder item = (ItemHolder) holder;
        item.primary.setText(row.getPrimary());
        item.secondary.setText(row.getSecondary());
        item.secondary.setVisibility(
                row.getSecondary().isEmpty() ? View.GONE : View.VISIBLE);

        if (!row.getDetail().isEmpty()) {
            CharSequence highlighted = MarkdownRenderer.render(
                    holder.itemView.getContext(), "```\n" + row.getDetail() + "\n```");
            item.detail.setText(highlighted);
            item.detail.setVisibility(View.VISIBLE);
        } else {
            item.detail.setVisibility(View.GONE);
        }

        item.itemView.setOnClickListener(ignored -> {
            if (listener != null) {
                listener.onItemClick(holder.getBindingAdapterPosition());
            }
        });
    }

    /** En-tête de section. */
    private static final class HeaderHolder extends RecyclerView.ViewHolder {
        private final TextView title;

        HeaderHolder(@NonNull View view) {
            super(view);
            this.title = view.findViewById(R.id.section_title);
        }
    }

    /** Ligne cliquable. */
    private static final class ItemHolder extends RecyclerView.ViewHolder {
        private final TextView primary;
        private final TextView secondary;
        private final TextView detail;

        ItemHolder(@NonNull View view) {
            super(view);
            this.primary = view.findViewById(R.id.row_primary);
            this.secondary = view.findViewById(R.id.row_secondary);
            this.detail = view.findViewById(R.id.row_detail);
        }
    }

    @Nullable
    @Override
    public String toString() {
        return "SectionListAdapter(" + rows.size() + " lignes)";
    }
}
