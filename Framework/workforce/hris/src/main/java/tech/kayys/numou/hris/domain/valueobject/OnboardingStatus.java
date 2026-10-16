package tech.kayys.syirkah.hris.domain.valueobject;

/**
 * Status of an onboarding process.
 */
public enum OnboardingStatus {
    INITIATED("Initiated - onboarding started"),
    DOCUMENTATION("Documentation - collecting documents"),
    ORIENTATION("Orientation - attending orientation"),
    TRAINING("Training - completing training"),
    EQUIPMENT("Equipment - receiving equipment"),
    MENTORSHIP("Mentorship - assigned mentor"),
    COMPLETED("Completed - onboarding finished"),
    FAILED("Failed - onboarding failed");

    private final String description;

    OnboardingStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return this != COMPLETED && this != FAILED;
    }
}
