package tech.kayys.syirkah.project.domain.commercial;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.Period;
import java.util.Objects;

/**
 * Explicit impact of a change: money, schedule and scope.
 *
 * Downstream domains (cost, schedule, billing) react to these deltas
 * through events; this record never reaches into them directly.
 */
public record ChangeImpact(
        Money priceDelta,
        Period scheduleDelta,
        boolean scopeChanged
) {

    public ChangeImpact {
        Objects.requireNonNull(
                priceDelta,
                "priceDelta cannot be null"
        );

        Objects.requireNonNull(
                scheduleDelta,
                "scheduleDelta cannot be null"
        );

        if (scheduleDelta.isNegative()) {
            throw new IllegalArgumentException(
                    "scheduleDelta cannot be negative"
            );
        }
    }

    public boolean changesPrice() {
        return priceDelta.amount().signum() != 0;
    }

    public boolean changesSchedule() {
        return !scheduleDelta.isZero();
    }
}