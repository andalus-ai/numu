
package tech.kayys.syirkah.purchasing.domain.event.procurement;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.util.UUID;

public record PurchaseOrderApproved(
        UUID eventId,
        Instant occurredAt,
        String tenantId,
        String ledgerId,
        String correlationId,
        String causationId,
        String poId,
        String vendorId,
        Money committedAmount,
        String approvedBy
) implements DomainEvent {
    public static PurchaseOrderApproved of(String tenantId, String ledgerId, String poId, String vendorId, Money committedAmount, String approvedBy, String corrId) {
        return new PurchaseOrderApproved(UUID.randomUUID(), Instant.now(), tenantId, ledgerId, corrId, null, poId, vendorId, committedAmount, approvedBy);
    }

    @Override
    public String eventType() { return "purchasing.purchase-order.approved"; }
}
