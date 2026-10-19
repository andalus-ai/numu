package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raised when the current location of an asset changes (see ASSET-12).
 *
 * <p>Carries the historical snapshot (id + name) so that ASSET-13 can build
 * movement history without reading back through the {@code Asset}.</p>
 */
public record AssetLocationChanged(
        UUID eventId,
        Instant occurredAt,
        UUID assetId,
        String locationId,
        String locationName
) implements DomainEvent {

    public AssetLocationChanged {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(locationId, "locationId cannot be null");
        Objects.requireNonNull(locationName, "locationName cannot be null");
    }

    @Override
    public String eventType() {
        return "asset.asset-location-changed";
    }
}
