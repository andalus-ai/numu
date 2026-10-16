package tech.kayys.syirkah.analytics.domain.valueobject;

/**
 * Frequency of report generation.
 */
public enum ReportFrequency {
    REAL_TIME("Real-Time"),
    ON_DEMAND("On Demand"),
    HOURLY("Hourly"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    YEARLY("Yearly"),
    CUSTOM("Custom");

    private final String displayName;

    ReportFrequency(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isScheduled() {
        return this != ON_DEMAND && this != REAL_TIME;
    }
}