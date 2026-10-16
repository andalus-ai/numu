package tech.kayys.syirkah.project.domain.commercial.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.commercial.ChangeOrderId;
import tech.kayys.syirkah.project.domain.commercial.ProjectContractId;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.Instant;
import java.time.Period;
import java.util.UUID;

public record ChangeOrderApproved(
        UUID eventId,
        Instant occurredAt,
        ChangeOrderId changeOrderId,
        ProjectId projectId,
        ProjectContractId contractId,
        Money priceDelta,
        Period scheduleDelta,
        boolean scopeChanged
) implements DomainEvent {

    @Override
    public String eventType() {
        return "project.change-order-approved";
    }
}