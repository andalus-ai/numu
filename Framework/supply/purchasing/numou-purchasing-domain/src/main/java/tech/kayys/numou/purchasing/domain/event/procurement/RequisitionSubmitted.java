
package tech.kayys.syirkah.purchasing.domain.event.procurement;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.util.UUID;

public record RequisitionSubmitted(
        UUID eventId,
        Instant occurredAt,
        String tenantId,
        String ledgerId,
        String correlationId,
        String causationId,
        String requisitionId,
        Money totalEstimatedAmount
) implements DomainEvent {
    public static RequisitionSubmitted of(String tenantId, String ledgerId, String requisitionId, Money total, String corrId) {
        return new RequisitionSubmitted(UUID.randomUUID(), Instant.now(), tenantId, ledgerId, corrId, null, requisitionId, total);
    }

    @Override
    public String eventType() { return "purchasing.requisition.submitted"; }
}
