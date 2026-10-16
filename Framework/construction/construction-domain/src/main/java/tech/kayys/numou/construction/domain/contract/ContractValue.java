package tech.kayys.syirkah.construction.domain.contract;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public record ContractValue(BigDecimal amount, Currency currency) {
    public ContractValue {
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(currency, "Currency cannot be null");
        if (amount.signum() < 0) throw new IllegalArgumentException("Contract value cannot be negative");
    }
}
