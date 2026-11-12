package dev.dystos.app.presentation.auth;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import dev.dystos.app.R;
import dev.dystos.app.core.OnboardingPreferences;
import dev.dystos.app.presentation.BaseFragment;
import dev.dystos.app.presentation.MainActivity;

/**
 * Écran proposé à la fin du questionnaire d'onboarding :
 * création de compte, connexion, ou passage direct dans l'application.
 */
public final class AccountPromptFragment extends BaseFragment {

    @Override
    protected int layoutId() {
        return R.layout.fragment_account_prompt;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.back_button).setOnClickListener(v -> {
            if (isAdded() && getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            }
        });

        view.findViewById(R.id.btn_register).setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).openRegister();
            }
        });

        view.findViewById(R.id.btn_login).setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).openLogin();
            }
        });

        view.findViewById(R.id.btn_skip).setOnClickListener(v -> enterApp());
    }

    private void enterApp() {
        OnboardingPreferences.setSeen(requireContext());
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).showMainNavigation();
        }
    }
}
