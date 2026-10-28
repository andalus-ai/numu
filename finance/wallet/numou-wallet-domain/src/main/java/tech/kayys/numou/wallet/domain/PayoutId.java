package tech.kayys.numou.wallet.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record PayoutId(UUID value) implements DomainId<UUID>, Serializable {

    public PayoutId {
        Objects.requireNonNull(value, "PayoutId value must not be null");
    }

    public static PayoutId of(UUID value) {
        return new PayoutId(value);
    }

    public static PayoutId generate() {
        return new PayoutId(UUID.randomUUID());
    }

    /** Deterministic id from an idempotency key: the same request always maps to the same payout. */
    public static PayoutId forKey(UUID tenantId, UUID driverId, String key) {
        return new PayoutId(UUID.nameUUIDFromBytes(("payout:" + tenantId + ":" + driverId + ":" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
