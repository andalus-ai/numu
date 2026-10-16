package tech.kayys.numou.workforce.domain.employment.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.employment.EmploymentType;
import tech.kayys.numou.workforce.domain.employment.OrganizationRef;
import tech.kayys.numou.workforce.domain.employment.PositionRef;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EmploymentStarted(
        UUID eventId,
        Instant occurredAt,
        EmploymentId employmentId,
        WorkerId workerId,
        OrganizationRef organization,
        PositionRef position,
        EmploymentType type,
        LocalDate startDate
) implements DomainEvent {

    public EmploymentStarted(
            EmploymentId employmentId,
            WorkerId workerId,
            OrganizationRef organization,
            PositionRef position,
            EmploymentType type,
            LocalDate startDate
    ) {
        this(UUID.randomUUID(), Instant.now(), employmentId, workerId, organization, position, type, startDate);
    }

    @Override
    public String eventType() {
        return "workforce.employment.started";
    }
}
