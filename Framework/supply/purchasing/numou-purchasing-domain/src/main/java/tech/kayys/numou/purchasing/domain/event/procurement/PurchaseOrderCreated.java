
package tech.kayys.syirkah.purchasing.domain.event.procurement;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PurchaseOrderCreated(
        UUID eventId,
        Instant occurredAt,
        String tenantId,
        String ledgerId,
        String correlationId,
        String causationId,
        String poId,
        String vendorId
) implements DomainEvent {
    public static PurchaseOrderCreated of(String tenantId, String ledgerId, String poId, String vendorId, String corrId) {
        return new PurchaseOrderCreated(UUID.randomUUID(), Instant.now(), tenantId, ledgerId, corrId, null, poId, vendorId);
    }

    @Override
    public String eventType() { return "purchasing.purchase-order.created"; }
}
