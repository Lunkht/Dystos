package dev.dystos.app.presentation.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import dev.dystos.app.R;
import dev.dystos.app.core.IdePreferences;
import dev.dystos.app.databinding.FragmentIdeSettingsBinding;
import dev.dystos.app.presentation.BaseFragment;
import dev.dystos.app.presentation.MainActivity;

/**
 * Paramètres de l'IDE : options d'affichage de l'éditeur (taille de police,
 * numéros de ligne, indentation, fermeture des paires).
 */
public final class IdeSettingsFragment extends BaseFragment {

    private FragmentIdeSettingsBinding binding;

    @Override
    protected int layoutId() {
        return R.layout.fragment_ide_settings;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentIdeSettingsBinding.bind(view);

        MainActivity activity = (MainActivity) requireActivity();
        view.findViewById(R.id.back_button).setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            } else {
                activity.goHome();
            }
        });

        bindEditorPreferences();
        binding.resetIdeSettings.setOnClickListener(ignored -> resetSettings());
    }

    private void bindEditorPreferences() {
        SeekBar fontSize = binding.fontSize;
        TextView fontSizeValue = binding.fontSizeValue;
        int initialSize = IdePreferences.fontSizeSp(requireContext());
        fontSize.setMax(10);
        fontSize.setOnSeekBarChangeListener(null);
        fontSize.setProgress(initialSize - 12);
        updateFontSizeLabel(fontSizeValue, initialSize);
        fontSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int size = 12 + progress;
                IdePreferences.setFontSizeSp(requireContext(), size);
                updateFontSizeLabel(fontSizeValue, size);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        binding.lineNumbersSwitch.setOnCheckedChangeListener(null);
        binding.autoIndentSwitch.setOnCheckedChangeListener(null);
        binding.autoClosePairsSwitch.setOnCheckedChangeListener(null);
        binding.indentTwoSpaces.setOnCheckedChangeListener(null);
        binding.indentFourSpaces.setOnCheckedChangeListener(null);
        binding.lineNumbersSwitch.setChecked(
                IdePreferences.lineNumbersEnabled(requireContext()));
        binding.autoIndentSwitch.setChecked(
                IdePreferences.autoIndentEnabled(requireContext()));
        binding.autoClosePairsSwitch.setChecked(
                IdePreferences.autoClosePairsEnabled(requireContext()));
        binding.indentTwoSpaces.setChecked(IdePreferences.indentSize(requireContext()) == 2);
        binding.indentFourSpaces.setChecked(IdePreferences.indentSize(requireContext()) == 4);

        binding.lineNumbersSwitch.setOnCheckedChangeListener((button, checked) ->
                IdePreferences.setLineNumbersEnabled(requireContext(), checked));
        binding.autoIndentSwitch.setOnCheckedChangeListener((button, checked) ->
                IdePreferences.setAutoIndentEnabled(requireContext(), checked));
        binding.autoClosePairsSwitch.setOnCheckedChangeListener((button, checked) ->
                IdePreferences.setAutoClosePairsEnabled(requireContext(), checked));
        binding.indentTwoSpaces.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                IdePreferences.setIndentSize(requireContext(), 2);
            }
        });
        binding.indentFourSpaces.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                IdePreferences.setIndentSize(requireContext(), 4);
            }
        });
    }

    private void updateFontSizeLabel(TextView label, int size) {
        label.setText(getString(R.string.ide_font_size_value, size));
    }

    private void resetSettings() {
        IdePreferences.reset(requireContext());
        bindEditorPreferences();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
