package tech.kayys.syirkah.accounting.domain.fund;

import java.util.Objects;
import java.util.UUID;

public record GrantId(String value) {
    public GrantId {
        Objects.requireNonNull(value, "GrantId value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("GrantId value must not be blank");
    }
    public static GrantId of(String value) { return new GrantId(value); }
    public static GrantId generate() { return new GrantId(UUID.randomUUID().toString()); }
}
