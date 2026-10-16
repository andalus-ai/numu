package tech.kayys.syirkah.project.domain.commercial.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.project.domain.commercial.ProjectContractId;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.UUID;

public record ProjectContractCompleted(
        UUID eventId,
        Instant occurredAt,
        ProjectContractId contractId,
        ProjectId projectId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "project.contract-completed";
    }
}