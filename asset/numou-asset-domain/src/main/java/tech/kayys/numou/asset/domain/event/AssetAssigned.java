package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised when custody of an asset is assigned to a party (see ASSET-12). */
public record AssetAssigned(
        UUID eventId,
        Instant occurredAt,
        UUID assetId,
        String partyId,
        String partyType,
        String partyName
) implements DomainEvent {

    public AssetAssigned {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(partyId, "partyId cannot be null");
        Objects.requireNonNull(partyType, "partyType cannot be null");
    }

    @Override
    public String eventType() {
        return "asset.asset-assigned";
    }
}
