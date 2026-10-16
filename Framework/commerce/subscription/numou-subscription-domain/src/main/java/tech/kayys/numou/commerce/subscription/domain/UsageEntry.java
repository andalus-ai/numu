package tech.kayys.syirkah.commerce.subscription.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * One metered consumption event (e.g. "3 GB of STORAGE at T").
 * Units are always positive; the code names the entitlement being
 * consumed.
 */
public record UsageEntry(
        String code,
        BigDecimal units,
        Instant at
) {

    public UsageEntry {
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(units, "units cannot be null");
        Objects.requireNonNull(at, "at cannot be null");
        code = code.trim();
        if (code.isBlank()) {
            throw new IllegalArgumentException("code cannot be blank");
        }
        if (units.signum() <= 0) {
            throw new IllegalArgumentException("units must be positive");
        }
    }
}
