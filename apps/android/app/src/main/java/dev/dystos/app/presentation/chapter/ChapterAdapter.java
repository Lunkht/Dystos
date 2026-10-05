package dev.dystos.app.presentation.chapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import dev.dystos.app.databinding.ItemChapterBinding;

/** Adaptateur de la liste des chapitres. */
final class ChapterAdapter extends RecyclerView.Adapter<ChapterAdapter.ChapterHolder> {

    interface OnChapterSelected {
        void onChapterSelected(ChapterListViewModel.ChapterItem item);
    }

    private final List<ChapterListViewModel.ChapterItem> items = new ArrayList<>();
    private final OnChapterSelected listener;

    ChapterAdapter(OnChapterSelected listener) {
        this.listener = listener;
    }

    void submit(List<ChapterListViewModel.ChapterItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChapterHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChapterBinding binding = ItemChapterBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ChapterHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ChapterHolder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ChapterHolder extends RecyclerView.ViewHolder {

        private final ItemChapterBinding binding;

        ChapterHolder(ItemChapterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ChapterListViewModel.ChapterItem item, OnChapterSelected listener) {
            binding.chapterTitle.setText(item.getTitle());
            binding.chapterCount.setText(binding.getRoot().getResources()
                    .getQuantityString(
                            dev.dystos.app.R.plurals.chapter_lesson_count,
                            item.getLessonCount(),
                            item.getLessonCount()));
            binding.chapterProgress.setText(binding.getRoot().getResources()
                    .getQuantityString(
                            dev.dystos.app.R.plurals.chapter_read_count,
                            item.getReadCount(),
                            item.getReadCount(),
                            item.getLessonCount()));

            int ratio = item.getLessonCount() == 0
                    ? 0
                    : Math.round(item.getReadCount() * 100f / item.getLessonCount());
            binding.chapterProgressBar.setProgress(ratio);

            binding.getRoot().setOnClickListener(view -> listener.onChapterSelected(item));
        }
    }
}