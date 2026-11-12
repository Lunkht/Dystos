package dev.dystos.app.presentation.onboarding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import dev.dystos.app.R;

public final class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.ViewHolder> {

    private final List<OnboardingPage> pages;

    public OnboardingAdapter(List<OnboardingPage> pages) {
        this.pages = pages;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_onboarding_page, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OnboardingPage page = pages.get(position);
        holder.title.setText(page.getTitle());
        holder.subtitle.setText(page.getSubtitle());
        holder.cubeNumber.setText(String.valueOf(page.getCubeNumber()));
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView cubeNumber;
        final TextView title;
        final TextView subtitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cubeNumber = itemView.findViewById(R.id.tv_cube_number);
            title = itemView.findViewById(R.id.tv_title);
            subtitle = itemView.findViewById(R.id.tv_subtitle);
        }
    }
}
