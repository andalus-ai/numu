package tech.kayys.numou.workforce.domain.paymentaccount;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record PaymentAccountId(UUID value) implements DomainId<UUID> {
    public PaymentAccountId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PaymentAccountId of(UUID value) { return new PaymentAccountId(value); }
    public static PaymentAccountId of(String value) { return new PaymentAccountId(UUID.fromString(value)); }
    public static PaymentAccountId generate() { return new PaymentAccountId(UUID.randomUUID()); }

    @Override
    public String toString() { return value.toString(); }
}
