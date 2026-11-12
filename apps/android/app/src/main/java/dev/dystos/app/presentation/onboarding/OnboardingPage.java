package dev.dystos.app.presentation.onboarding;

public final class OnboardingPage {
    private final String title;
    private final String subtitle;
    private final int cubeNumber;

    public OnboardingPage(String title, String subtitle, int cubeNumber) {
        this.title = title;
        this.subtitle = subtitle;
        this.cubeNumber = cubeNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public int getCubeNumber() {
        return cubeNumber;
    }
}
