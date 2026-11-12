package dev.dystos.app.presentation.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import java.util.Arrays;
import java.util.List;

import dev.dystos.app.R;
import dev.dystos.app.core.OnboardingPreferences;
import dev.dystos.app.presentation.MainActivity;

public final class OnboardingFragment extends Fragment {

    private ViewPager2 viewPager;
    private LinearLayout dotsLayout;
    private View btnGetStarted;
    private TextView btnLogin;

    private List<OnboardingPage> pages;
    private TextView[] dotViews;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_onboarding, container, false);

        viewPager = view.findViewById(R.id.view_pager);
        dotsLayout = view.findViewById(R.id.dots_layout);
        btnGetStarted = view.findViewById(R.id.btn_get_started);
        btnLogin = view.findViewById(R.id.btn_login);

        pages = Arrays.asList(
                new OnboardingPage(
                        getString(R.string.onboarding_title_1),
                        getString(R.string.onboarding_subtitle_1),
                        1
                ),
                new OnboardingPage(
                        getString(R.string.onboarding_title_2),
                        getString(R.string.onboarding_subtitle_2),
                        2
                ),
                new OnboardingPage(
                        getString(R.string.onboarding_title_3),
                        getString(R.string.onboarding_subtitle_3),
                        3
                ),
                new OnboardingPage(
                        getString(R.string.onboarding_title_4),
                        getString(R.string.onboarding_subtitle_4),
                        4
                )
        );

        OnboardingAdapter adapter = new OnboardingAdapter(pages);
        viewPager.setAdapter(adapter);

        setupDots(pages.size());
        updateDots(0);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateDots(position);
            }
        });

        btnGetStarted.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current < pages.size() - 1) {
                viewPager.setCurrentItem(current + 1, true);
            } else {
                finishOnboarding();
            }
        });

        btnLogin.setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).openLogin();
            }
        });

        View btnRegister = view.findViewById(R.id.btn_register);
        btnRegister.setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).openRegister();
            }
        });

        return view;
    }

    private void setupDots(int count) {
        dotViews = new TextView[count];
        dotsLayout.removeAllViews();
        for (int i = 0; i < count; i++) {
            dotViews[i] = new TextView(requireContext());
            dotViews[i].setText("•");
            dotViews[i].setTextSize(24);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(6, 0, 6, 0);
            dotViews[i].setLayoutParams(params);
            dotsLayout.addView(dotViews[i]);
        }
    }

    private void updateDots(int activeIndex) {
        for (int i = 0; i < dotViews.length; i++) {
            if (i == activeIndex) {
                dotViews[i].setTextColor(getResources().getColor(
                        R.color.dystos_primary, requireContext().getTheme()));
            } else {
                dotViews[i].setTextColor(getResources().getColor(
                        R.color.dystos_outline, requireContext().getTheme()));
            }
        }
    }

    private void finishOnboarding() {
        OnboardingPreferences.setSeen(requireContext());
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new SurveyFragment())
                .commit();
    }
}
