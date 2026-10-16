package tech.kayys.syirkah.accounting.application.posting;

import java.math.BigDecimal;
import java.util.Objects;

public record PostingLine(String accountCode, BigDecimal amount, Side side) {
    public PostingLine {
        if (accountCode == null || accountCode.isBlank()) throw new IllegalArgumentException("accountCode must not be blank");
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("posting amount must be positive");
        Objects.requireNonNull(side);
    }
    public enum Side { DEBIT, CREDIT }
}
