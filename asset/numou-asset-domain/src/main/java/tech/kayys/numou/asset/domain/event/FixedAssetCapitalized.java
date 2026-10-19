package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Accounting → Asset: a fixed asset was capitalized (ASSET-25 §16). */
public record FixedAssetCapitalized(
        UUID eventId,
        Instant occurredAt,
        UUID accountingAssetId,
        UUID operationalAssetId,
        String accountingAssetNumber,
        BigDecimal acquisitionCost,
        String currency,
        Instant capitalizationDate
) implements DomainEvent {

    public FixedAssetCapitalized {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(accountingAssetId, "accountingAssetId cannot be null");
        Objects.requireNonNull(operationalAssetId, "operationalAssetId cannot be null");
    }

    @Override
    public String eventType() {
        return "accounting.fixed-asset-capitalized";
    }
}
