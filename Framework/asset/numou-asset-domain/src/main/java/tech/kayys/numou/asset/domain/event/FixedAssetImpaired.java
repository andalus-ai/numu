package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Accounting → Asset: a fixed asset was impaired (ASSET-25 §24). The amount is not duplicated in Asset. */
public record FixedAssetImpaired(
        UUID eventId,
        Instant occurredAt,
        UUID accountingAssetId,
        UUID operationalAssetId,
        BigDecimal impairmentAmount,
        String currency
) implements DomainEvent {

    public FixedAssetImpaired {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(accountingAssetId, "accountingAssetId cannot be null");
        Objects.requireNonNull(operationalAssetId, "operationalAssetId cannot be null");
    }

    @Override
    public String eventType() {
        return "accounting.fixed-asset-impaired";
    }
}
