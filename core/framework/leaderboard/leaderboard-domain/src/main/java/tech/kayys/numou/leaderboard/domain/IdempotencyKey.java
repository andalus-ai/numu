package tech.kayys.numou.leaderboard.domain;

import java.util.Objects;

/** Caller-provided stable key for exactly-once ledger acceptance semantics. */
public record IdempotencyKey(String value) {
    public IdempotencyKey {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("idempotency key must not be blank");
        if (value.length() > 200) throw new IllegalArgumentException("idempotency key exceeds 200 characters");
    }
}