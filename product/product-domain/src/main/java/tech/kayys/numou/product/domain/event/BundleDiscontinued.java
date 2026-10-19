package tech.kayys.numou.product.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.product.domain.bundle.BundleId;

import java.time.Instant;
import java.util.UUID;

public record BundleDiscontinued(
        UUID eventId,
        Instant occurredAt,
        BundleId bundleId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.bundle-discontinued";
    }
}
