package dev.dystos.app.presentation.settings;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import dev.dystos.app.R;
import dev.dystos.app.presentation.BaseFragment;

public final class FaqFragment extends BaseFragment {
    @Override
    protected int layoutId() {
        return R.layout.fragment_faq;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.back_button).setOnClickListener(v -> {
            if (isAdded()) {
                getParentFragmentManager().popBackStack();
            }
        });
    }
}
