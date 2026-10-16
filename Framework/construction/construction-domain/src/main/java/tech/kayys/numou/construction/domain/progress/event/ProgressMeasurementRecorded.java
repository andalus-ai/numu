package tech.kayys.syirkah.construction.domain.progress.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record ProgressMeasurementRecorded(
        UUID eventId,
        Instant occurredAt,
        UUID measurementId,
        UUID projectId,
        UUID boqItemId
) implements DomainEvent {
    @Override public String eventType() { return "construction.progress-measurement-recorded"; }
}
