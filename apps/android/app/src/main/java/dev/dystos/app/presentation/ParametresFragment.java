package dev.dystos.app.presentation;

import android.os.Bundle;
import android.view.View;

import dev.dystos.app.R;
import dev.dystos.app.databinding.FragmentParametresBinding;

/**
 * Onglet Paramètres : bascule de thème, langue et informations de l'application.
 */
public final class ParametresFragment extends BaseFragment {

    private FragmentParametresBinding binding;

    @Override
    protected int layoutId() {
        return R.layout.fragment_parametres;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentParametresBinding.bind(view);

        // Initialiser l'état du switch selon le thème actuel
        binding.themeSwitch.setChecked(
                dev.dystos.app.core.ThemePreferences.isDark(requireContext()));

        binding.themeSwitch.setOnCheckedChangeListener((btn, isChecked) ->
                ((MainActivity) requireActivity()).toggleTheme());

        MainActivity activity = (MainActivity) requireActivity();
        view.findViewById(R.id.back_button).setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            } else {
                activity.goHome();
            }
        });
        view.findViewById(R.id.row_faq).setOnClickListener(v -> activity.openFaq());
        view.findViewById(R.id.row_about).setOnClickListener(v -> activity.openAbout());
        view.findViewById(R.id.row_update).setOnClickListener(v -> activity.openUpdateCheck());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
