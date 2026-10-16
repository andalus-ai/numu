package tech.kayys.syirkah.project.domain.commercial.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.commercial.ProjectClaimId;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.UUID;

public record ClaimAccepted(
        UUID eventId,
        Instant occurredAt,
        ProjectClaimId claimId,
        ProjectId projectId,
        Money claimedAmount,
        Money acceptedAmount
) implements DomainEvent {

    @Override
    public String eventType() {
        return "project.claim-accepted";
    }
}