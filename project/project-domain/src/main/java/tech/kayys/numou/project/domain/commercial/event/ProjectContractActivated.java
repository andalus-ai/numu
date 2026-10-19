package tech.kayys.numou.project.domain.commercial.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.project.domain.commercial.ProjectContractId;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.UUID;

public record ProjectContractActivated(
        UUID eventId,
        Instant occurredAt,
        ProjectContractId contractId,
        ProjectId projectId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "project.contract-activated";
    }
}