package tech.kayys.syirkah.product.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.product.domain.bundle.BundleId;

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
