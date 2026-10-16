
package tech.kayys.syirkah.purchasing.domain.event.procurement;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record RequisitionApproved(
        UUID eventId,
        Instant occurredAt,
        String tenantId,
        String ledgerId,
        String correlationId,
        String causationId,
        String requisitionId,
        String approvedBy
) implements DomainEvent {
    public static RequisitionApproved of(String tenantId, String ledgerId, String requisitionId, String approvedBy, String corrId) {
        return new RequisitionApproved(UUID.randomUUID(), Instant.now(), tenantId, ledgerId, corrId, null, requisitionId, approvedBy);
    }

    @Override
    public String eventType() { return "purchasing.requisition.approved"; }
}
