package tech.kayys.numou.history.events.domain;

import tech.kayys.numou.history.chronology.domain.HistoricalChronology;

import java.util.Objects;

/** Domain policy for validating nested period ranges across supported calendars. */
public final class HistoricalPeriodHierarchy {
    private HistoricalPeriodHierarchy() {
    }

    /**
     * Validates a child period against its declared parent. Parent and child
     * identities are checked by the caller's repository/application boundary;
     * this policy checks the temporal invariant only.
     */
    public static void assertChildWithinParent(
            HistoricalPeriod parent, HistoricalPeriod child, HistoricalChronology chronology) {
        Objects.requireNonNull(parent, "Parent period is required");
        Objects.requireNonNull(child, "Child period is required");
        Objects.requireNonNull(chronology, "Chronology is required");
        if (!parent.id().equals(child.parentPeriodId())) {
            throw new IllegalArgumentException("Child period must reference the supplied parent period");
        }
        boolean contained = chronology.contains(parent.dateRange(), child.dateRange())
                .orElseThrow(() -> new IllegalArgumentException("Cannot validate period containment: calendar adapter is unavailable"));
        if (!contained) {
            throw new IllegalArgumentException("Child period range must be fully contained within its parent range");
        }
    }
}
