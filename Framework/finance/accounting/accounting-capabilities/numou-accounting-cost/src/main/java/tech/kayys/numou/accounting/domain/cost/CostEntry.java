package tech.kayys.syirkah.accounting.domain.cost;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Single recorded cost ledger transaction line. */
public record CostEntry(
        String id,
        CostCenterId costCenterId,
        CostSource source,
        String accountCode,
        BigDecimal amount,
        String currency,
        String referenceNumber,
        Instant occurredAt
) {
    public CostEntry {
        Objects.requireNonNull(id);
        Objects.requireNonNull(costCenterId);
        Objects.requireNonNull(source);
        Objects.requireNonNull(accountCode);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(occurredAt);
    }
    public static CostEntry of(CostCenterId ccId, CostSource src, String account, BigDecimal amt, String curr, String ref) {
        return new CostEntry(UUID.randomUUID().toString(), ccId, src, account, amt, curr, ref, Instant.now());
    }
}
