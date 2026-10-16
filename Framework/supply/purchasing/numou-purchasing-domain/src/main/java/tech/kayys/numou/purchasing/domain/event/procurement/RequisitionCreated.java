
package tech.kayys.syirkah.purchasing.domain.event.procurement;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record RequisitionCreated(
        UUID eventId,
        Instant occurredAt,
        String tenantId,
        String ledgerId,
        String correlationId,
        String causationId,
        String requisitionId,
        String departmentId
) implements DomainEvent {
    public static RequisitionCreated of(String tenantId, String ledgerId, String requisitionId, String deptId, String corrId) {
        return new RequisitionCreated(UUID.randomUUID(), Instant.now(), tenantId, ledgerId, corrId, null, requisitionId, deptId);
    }

    @Override
    public String eventType() { return "purchasing.requisition.created"; }
}
