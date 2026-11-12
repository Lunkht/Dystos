package dev.dystos.app.presentation.onboarding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import dev.dystos.app.R;

public final class SkillsAdapter extends RecyclerView.Adapter<SkillsAdapter.ViewHolder> {

    private final List<SkillItem> skills;
    private final Runnable onSelectionChanged;

    public SkillsAdapter(List<SkillItem> skills, Runnable onSelectionChanged) {
        this.skills = skills;
        this.onSelectionChanged = onSelectionChanged;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_skill_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SkillItem skill = skills.get(position);
        holder.name.setText(skill.getName());
        holder.learners.setText(skill.getLearners());

        if (skill.isSelected()) {
            paint(holder.card,
                    holder.card.getContext().getColor(R.color.dystos_primary), 3,
                    holder.card.getContext().getColor(R.color.dystos_surface_variant));
        } else {
            paint(holder.card,
                    holder.card.getContext().getColor(R.color.dystos_outline), 1,
                    holder.card.getContext().getColor(R.color.dystos_surface));
        }

        holder.card.setOnClickListener(v -> {
            skill.setSelected(!skill.isSelected());
            notifyItemChanged(position);
            onSelectionChanged.run();
        });
    }

    @Override
    public int getItemCount() {
        return skills.size();
    }

    /** Contour et fond de la carte, sans dépendre de MaterialCardView. */
    private static void paint(View view, int strokeColor, int strokeWidthDp, int fillColor) {
        android.graphics.drawable.Drawable background = view.getBackground();
        android.graphics.drawable.GradientDrawable shape;
        if (background instanceof android.graphics.drawable.GradientDrawable) {
            shape = (android.graphics.drawable.GradientDrawable) background.mutate();
        } else {
            shape = new android.graphics.drawable.GradientDrawable();
            shape.setCornerRadius(dp(view, 10));
            view.setBackground(shape);
        }
        shape.setStroke(Math.round(strokeWidthDp * density(view)), strokeColor);
        shape.setColor(fillColor);
    }

    private static float density(View view) {
        return view.getResources().getDisplayMetrics().density;
    }

    private static int dp(View view, int value) {
        return Math.round(value * density(view));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View card;
        final TextView name;
        final TextView learners;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView;
            name = itemView.findViewById(R.id.tv_skill_name);
            learners = itemView.findViewById(R.id.tv_skill_learners);
        }
    }
}
