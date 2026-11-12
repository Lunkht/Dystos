package dev.dystos.app.presentation.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

import dev.dystos.app.R;
import dev.dystos.app.core.OnboardingPreferences;
import dev.dystos.app.presentation.BaseFragment;
import dev.dystos.app.presentation.MainActivity;

public final class LoginFragment extends BaseFragment {

    @Override
    protected int layoutId() {
        return R.layout.fragment_login;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.back_button).setOnClickListener(v -> {
            if (isAdded()) {
                getParentFragmentManager().popBackStack();
            }
        });

        MaterialButton btnLogin = view.findViewById(R.id.btn_login);
        btnLogin.setOnClickListener(v -> {
            OnboardingPreferences.setSeen(requireContext());
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).showMainNavigation();
            }
        });

        TextView btnRegister = view.findViewById(R.id.btn_go_register);
        btnRegister.setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).openRegister();
            }
        });
    }
}
