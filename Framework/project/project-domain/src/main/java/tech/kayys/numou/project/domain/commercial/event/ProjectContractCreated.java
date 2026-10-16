package tech.kayys.syirkah.project.domain.commercial.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.project.domain.commercial.ContractType;
import tech.kayys.syirkah.project.domain.commercial.ProjectContractId;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.UUID;

public record ProjectContractCreated(
        UUID eventId,
        Instant occurredAt,
        ProjectContractId contractId,
        ProjectId projectId,
        ContractType contractType
) implements DomainEvent {

    @Override
    public String eventType() {
        return "project.contract-created";
    }
}