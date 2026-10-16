package tech.kayys.syirkah.construction.domain.field.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DailyFieldReportSubmitted(
        UUID eventId,
        Instant occurredAt,
        UUID reportId,
        UUID siteId,
        LocalDate reportDate
) implements DomainEvent {
    @Override public String eventType() { return "construction.daily-field-report-submitted"; }
}
