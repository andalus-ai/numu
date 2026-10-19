package tech.kayys.numou.reservation.domain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.util.Objects;

public record CancellationOutcome(Charge charge, Money chargeAmount, Money refundAmount) {

    public enum Charge {
        NONE,
        LATE_FEE,
        DEPOSIT_FORFEITED
    }

    public CancellationOutcome {
        Objects.requireNonNull(charge, "charge cannot be null");
        Objects.requireNonNull(chargeAmount, "chargeAmount cannot be null");
        Objects.requireNonNull(refundAmount, "refundAmount cannot be null");
    }
}
