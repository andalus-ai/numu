package tech.kayys.numou.omnichannel.core;

import java.util.Objects;

/**
 * Stable client/provider request key. Persistence adapters must scope the key
 * by tenant, provider account, operation and retention policy; this value alone
 * is not a globally unique database key.
 */
public record IdempotencyKey(String value) {
    public IdempotencyKey {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
        if (value.length() > 200) {
            throw new IllegalArgumentException("idempotency key must not exceed 200 characters");
        }
    }
}
