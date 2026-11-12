package dev.dystos.app.presentation.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

import dev.dystos.app.R;
import dev.dystos.app.presentation.BaseFragment;

public final class UpdateCheckFragment extends BaseFragment {
    @Override
    protected int layoutId() {
        return R.layout.fragment_update_check;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.back_button).setOnClickListener(v -> {
            if (isAdded()) {
                getParentFragmentManager().popBackStack();
            }
        });

        MaterialButton btnCheck = view.findViewById(R.id.btn_check_again);
        btnCheck.setOnClickListener(v -> Toast.makeText(requireContext(), "Vous utilisez la dernière version !", Toast.LENGTH_SHORT).show());
    }
}
