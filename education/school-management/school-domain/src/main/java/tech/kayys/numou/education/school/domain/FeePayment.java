package tech.kayys.numou.education.school.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A payment received against a fee invoice. */
public record FeePayment(BigDecimal amount, LocalDate paidOn, String method) {
    public FeePayment {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Payment amount must be positive");
        if (paidOn == null) throw new IllegalArgumentException("paidOn must not be null");
    }
}
