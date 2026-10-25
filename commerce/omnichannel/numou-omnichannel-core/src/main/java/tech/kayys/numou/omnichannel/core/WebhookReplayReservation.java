package tech.kayys.numou.omnichannel.core;

import java.time.Instant;
import java.util.Objects;

/** Hashed, scoped replay reservation. Raw provider replay tokens must never be persisted. */
public record WebhookReplayReservation(
        String providerId, String providerAccountId, String replayKeySha256,
        Instant now, Instant expiresAt) {
    public WebhookReplayReservation {
        providerId = required(providerId, "providerId", 100);
        providerAccountId = required(providerAccountId, "providerAccountId", 200);
        replayKeySha256 = required(replayKeySha256, "replayKeySha256", 64);
        if (!replayKeySha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("replayKeySha256 must be lowercase SHA-256 hex");
        }
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(now)) throw new IllegalArgumentException("expiresAt must be after now");
    }
    private static String required(String value, String field, int max) {
        Objects.requireNonNull(value, field + " must not be null");
        String result = value.trim();
        if (result.isEmpty() || result.length() > max) throw new IllegalArgumentException(field + " is invalid");
        return result;
    }
}