package tech.kayys.syirkah.commerce.subscription.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * One thing a subscription unlocks (blueprint §10), e.g.
 * {@code API_ACCESS}, {@code 100GB_STORAGE}, {@code GYM_ACCESS}.
 *
 * Either a plain flag (no limit) or a metered quota for the current
 * billing period. Entitlements belong to the plan (offering), not to
 * the subscriber — they are looked up by offering id at check time.
 */
public record Entitlement(
        String code,
        OptionalLong limit
) {

    public Entitlement {
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(limit, "limit cannot be null");
        code = code.trim();
        if (code.isBlank()) {
            throw new IllegalArgumentException("code cannot be blank");
        }
        if (limit.isPresent() && limit.getAsLong() <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }

    /** Unmetered entitlement ("API_ACCESS"). */
    public static Entitlement flag(String code) {
        return new Entitlement(code, OptionalLong.empty());
    }

    /** Metered entitlement ("100GB_STORAGE" -> limit 100). */
    public static Entitlement limited(String code, long limit) {
        return new Entitlement(code, OptionalLong.of(limit));
    }

    /** Whether consuming {@code consumed} in total stays within quota. */
    public boolean allows(BigDecimal consumed) {
        Objects.requireNonNull(consumed, "consumed cannot be null");
        return limit.isEmpty()
                || consumed.compareTo(BigDecimal.valueOf(limit.getAsLong())) <= 0;
    }

    /** Remaining quota; empty when unmetered (unlimited). */
    public Optional<BigDecimal> remaining(BigDecimal consumed) {
        Objects.requireNonNull(consumed, "consumed cannot be null");
        return limit.stream()
                .mapToObj(l -> BigDecimal.valueOf(l).subtract(consumed).max(BigDecimal.ZERO))
                .findFirst();
    }
}
