package tech.kayys.numou.project.domain.commercial.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.project.domain.commercial.ProjectClaimId;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.UUID;

public record ClaimSettled(
        UUID eventId,
        Instant occurredAt,
        ProjectClaimId claimId,
        ProjectId projectId,
        Money settledAmount
) implements DomainEvent {

    @Override
    public String eventType() {
        return "project.claim-settled";
    }
}