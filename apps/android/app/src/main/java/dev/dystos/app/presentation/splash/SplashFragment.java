package dev.dystos.app.presentation.splash;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import dev.dystos.app.R;
import dev.dystos.app.core.OnboardingPreferences;
import dev.dystos.app.presentation.MainActivity;
import dev.dystos.app.presentation.chapter.ChapterListFragment;
import dev.dystos.app.presentation.onboarding.OnboardingFragment;

public final class SplashFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_splash, container, false);

        // Masquer la barre de navigation pendant le splash
        if (requireActivity() instanceof MainActivity) {
            View bottomNav = requireActivity().findViewById(R.id.bottom_nav);
            if (bottomNav != null) {
                bottomNav.setVisibility(View.GONE);
            }
        }

        // Délai de 1.2 secondes puis transition vers l'onboarding ou l'accueil
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isAdded()) {
                return;
            }
            boolean seen = OnboardingPreferences.isSeen(requireContext());
            Fragment nextFragment = seen ? new ChapterListFragment() : new OnboardingFragment();

            if (seen && requireActivity() instanceof MainActivity) {
                View bottomNav = requireActivity().findViewById(R.id.bottom_nav);
                if (bottomNav != null) {
                    bottomNav.setVisibility(View.VISIBLE);
                }
            }

            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(R.anim.screen_fade_in, R.anim.screen_fade_out)
                    .replace(R.id.fragment_container, nextFragment)
                    .commit();
        }, 1200);

        return view;
    }
}
