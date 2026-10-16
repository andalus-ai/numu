package tech.kayys.syirkah.analytics.domain.valueobject;

/**
 * Status of a dashboard.
 */
public enum DashboardStatus {
    DRAFT("Draft - being designed"),
    PUBLISHED("Published - available to users"),
    ARCHIVED("Archived - no longer active"),
    UNDER_MAINTENANCE("Under Maintenance - being updated");

    private final String description;

    DashboardStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return this == PUBLISHED;
    }
}