package tech.kayys.syirkah.product.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.product.domain.bundle.BundleId;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.time.Instant;
import java.util.UUID;

public record BundleComponentRemoved(
        UUID eventId,
        Instant occurredAt,
        BundleId bundleId,
        ProductId productId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.bundle-component-removed";
    }
}
