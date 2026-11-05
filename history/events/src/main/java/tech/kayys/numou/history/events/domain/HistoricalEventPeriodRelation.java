package tech.kayys.numou.history.events.domain;

import tech.kayys.numou.history.chronology.domain.HistoricalChronology;
import tech.kayys.numou.history.chronology.domain.TemporalRelation;

import java.util.Objects;
import java.util.Optional;

/**
 * Computes temporal relation between an event and a period. This is a
 * projection/query rule, not a claim that the period caused the event.
 */
public final class HistoricalEventPeriodRelation {
    private HistoricalEventPeriodRelation() {
    }

    public static Optional<TemporalRelation> relate(
            HistoricalEvent event, HistoricalPeriod period, HistoricalChronology chronology) {
        Objects.requireNonNull(event, "Event is required");
        Objects.requireNonNull(period, "Period is required");
        Objects.requireNonNull(chronology, "Chronology is required");
        return chronology.compare(event.dateRange(), period.dateRange());
    }
}
