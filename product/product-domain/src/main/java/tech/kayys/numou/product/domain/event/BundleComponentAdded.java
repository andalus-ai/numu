package tech.kayys.numou.product.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.product.domain.bundle.BundleId;
import tech.kayys.numou.product.domain.product.ProductId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BundleComponentAdded(
        UUID eventId,
        Instant occurredAt,
        BundleId bundleId,
        ProductId productId,
        BigDecimal quantity
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.bundle-component-added";
    }
}
