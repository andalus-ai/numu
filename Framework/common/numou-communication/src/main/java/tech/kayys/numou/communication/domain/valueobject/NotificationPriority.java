package tech.kayys.syirkah.communication.domain.valueobject;

/**
 * Priority levels for notifications.
 */
public enum NotificationPriority {
    CRITICAL("Critical - immediate attention required"),
    HIGH("High - urgent"),
    MEDIUM("Medium - normal priority"),
    LOW("Low - informational"),
    TRIVIAL("Trivial - nice to know");

    private final String description;

    NotificationPriority(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public int getSeverity() {
        return switch (this) {
            case CRITICAL -> 1;
            case HIGH -> 2;
            case MEDIUM -> 3;
            case LOW -> 4;
            case TRIVIAL -> 5;
        };
    }

    public boolean requiresImmediateAttention() {
        return this == CRITICAL || this == HIGH;
    }
}
