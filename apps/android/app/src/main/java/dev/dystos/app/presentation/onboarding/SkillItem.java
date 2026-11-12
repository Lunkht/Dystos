package dev.dystos.app.presentation.onboarding;

public final class SkillItem {
    private final String name;
    private final String learners;
    private boolean selected;

    public SkillItem(String name, String learners) {
        this.name = name;
        this.learners = learners;
        this.selected = false;
    }

    public String getName() {
        return name;
    }

    public String getLearners() {
        return learners;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
