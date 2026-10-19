package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Asset → Accounting: the operational link was established or replaced (ASSET-25 §§30-31, via outbox). */
public record AssetAccountingLinked(
        UUID eventId,
        Instant occurredAt,
        UUID assetId,
        UUID accountingAssetId
) implements DomainEvent {

    public AssetAccountingLinked {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(accountingAssetId, "accountingAssetId cannot be null");
    }

    @Override
    public String eventType() {
        return "asset.accounting-linked";
    }
}
