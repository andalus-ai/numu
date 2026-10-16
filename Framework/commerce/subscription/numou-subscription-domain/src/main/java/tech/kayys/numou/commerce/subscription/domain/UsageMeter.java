package tech.kayys.syirkah.commerce.subscription.domain;

import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Consumption counters for one subscription within one billing
 * period. Meters reset every renewal (a new period gets a new
 * meter), so quota is always period-scoped — the classic
 * "100GB_STORAGE per month" reading of blueprint §10.
 *
 * Mutable by design: entries accumulate in place inside a single
 * consistency boundary, and identity is (subscription, period).
 */
public final class UsageMeter {

    private final SubscriptionId subscriptionId;
    private final DateRange period;
    private final Map<String, BigDecimal> consumed = new LinkedHashMap<>();

    public UsageMeter(SubscriptionId subscriptionId, DateRange period) {
        this.subscriptionId = Objects.requireNonNull(
                subscriptionId, "subscriptionId cannot be null");
        this.period = Objects.requireNonNull(period, "period cannot be null");
    }

    /**
     * Accumulates one entry against its entitlement, enforcing both
     * period membership and the entitlement's quota.
     *
     * @return the new running total for the code
     * @throws IllegalArgumentException when the entry falls outside
     *         the metering period, names a different entitlement, or
     *         would exceed a metered quota
     */
    public BigDecimal record(UsageEntry entry, Entitlement entitlement) {
        Objects.requireNonNull(entry, "entry cannot be null");
        Objects.requireNonNull(entitlement, "entitlement cannot be null");
        if (!entitlement.code().equals(entry.code())) {
            throw new IllegalArgumentException(
                    "Entry code " + entry.code()
                            + " does not match entitlement " + entitlement.code());
        }
        var date = entry.at().atZone(ZoneOffset.UTC).toLocalDate();
        if (!period.contains(date)) {
            throw new IllegalArgumentException(
                    "Usage entry is outside the metering period "
                            + period.start() + ".." + period.end());
        }
        var projected = consumedOf(entry.code()).add(entry.units());
        if (!entitlement.allows(projected)) {
            throw new IllegalArgumentException(
                    "Quota exceeded for " + entitlement.code()
                            + " (limit " + entitlement.limit().getAsLong() + ")");
        }
        consumed.put(entry.code(), projected);
        return projected;
    }

    public BigDecimal consumedOf(String code) {
        return consumed.getOrDefault(code, BigDecimal.ZERO);
    }

    public Optional<BigDecimal> remaining(Entitlement entitlement) {
        return entitlement.remaining(consumedOf(entitlement.code()));
    }

    public SubscriptionId subscriptionId() {
        return subscriptionId;
    }

    public DateRange period() {
        return period;
    }
}
