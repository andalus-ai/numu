package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised when an asset is given a business classification (see ASSET-15). */
public record AssetClassified(
        UUID eventId,
        Instant occurredAt,
        UUID assetId,
        String classificationId,
        String classificationName
) implements DomainEvent {

    public AssetClassified {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(classificationId, "classificationId cannot be null");
        Objects.requireNonNull(classificationName, "classificationName cannot be null");
    }

    @Override
    public String eventType() {
        return "asset.asset-classified";
    }
}
