package dev.dystos.app.presentation.onboarding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import dev.dystos.app.R;

public final class SurveyAdapter extends RecyclerView.Adapter<SurveyAdapter.ViewHolder> {

    private final List<String> options;
    private final OnOptionClickListener listener;

    public interface OnOptionClickListener {
        void onOptionClick(int position, String option);
    }

    public SurveyAdapter(List<String> options, OnOptionClickListener listener) {
        this.options = options;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_survey_option, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String option = options.get(position);
        holder.textView.setText(option);
        holder.itemView.setOnClickListener(v -> listener.onOptionClick(position, option));
    }

    @Override
    public int getItemCount() {
        return options.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.btn_option);
        }
    }
}
