package tech.kayys.syirkah.project.domain.commercial.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.project.domain.commercial.ChangeOrderId;
import tech.kayys.syirkah.project.domain.commercial.ProjectContractId;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.UUID;

public record ChangeOrderCancelled(
        UUID eventId,
        Instant occurredAt,
        ChangeOrderId changeOrderId,
        ProjectId projectId,
        ProjectContractId contractId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "project.change-order-cancelled";
    }
}