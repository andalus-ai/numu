package tech.kayys.syirkah.product.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.product.domain.bundle.BundleId;

import java.time.Instant;
import java.util.UUID;

public record BundleActivated(
        UUID eventId,
        Instant occurredAt,
        BundleId bundleId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.bundle-activated";
    }
}
