package tech.kayys.syirkah.accounting.domain.fund;

import java.util.Objects;
import java.util.UUID;

public record FundId(String value) {
    public FundId {
        Objects.requireNonNull(value, "FundId value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("FundId value must not be blank");
    }
    public static FundId of(String value) { return new FundId(value); }
    public static FundId generate() { return new FundId(UUID.randomUUID().toString()); }
}
