package tech.kayys.numou.workforce.domain.employment.onboarding;

import java.time.Instant;
import java.util.Objects;

public final class OnboardingTask {
    private final String id;
    private final String name;
    private final String description;
    private final boolean required;
    private boolean completed;
    private String completedBy;
    private Instant completedAt;

    public OnboardingTask(String id, String name, String description, boolean required) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = description;
        this.required = required;
        this.completed = false;
    }

    public void complete(String completedBy, Instant now) {
        this.completed = true;
        this.completedBy = Objects.requireNonNull(completedBy, "completedBy must not be null");
        this.completedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isRequired() { return required; }
    public boolean isCompleted() { return completed; }
    public String getCompletedBy() { return completedBy; }
    public Instant getCompletedAt() { return completedAt; }
}
