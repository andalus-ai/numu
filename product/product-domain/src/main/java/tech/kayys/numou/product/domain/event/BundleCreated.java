package tech.kayys.numou.product.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.product.domain.bundle.BundleId;

import java.time.Instant;
import java.util.UUID;

public record BundleCreated(
        UUID eventId,
        Instant occurredAt,
        BundleId bundleId,
        String code,
        String name
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.bundle-created";
    }
}
