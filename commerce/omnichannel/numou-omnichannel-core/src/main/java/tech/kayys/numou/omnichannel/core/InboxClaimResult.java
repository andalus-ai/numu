package tech.kayys.numou.omnichannel.core;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of attempting to atomically claim an inbound provider event.
 * For a duplicate or conflict, existingPayloadSha256 contains the persisted
 * digest. A conflict must be quarantined/alerted, never silently overwritten.
 */
public record InboxClaimResult(
        InboundEventKey key,
        InboxClaimDisposition disposition,
        Optional<String> existingPayloadSha256) {

    public InboxClaimResult {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(disposition, "disposition must not be null");
        Objects.requireNonNull(existingPayloadSha256, "existingPayloadSha256 must not be null");
        existingPayloadSha256 = existingPayloadSha256.map(String::trim);
        if (disposition == InboxClaimDisposition.ACCEPTED && existingPayloadSha256.isPresent()) {
            throw new IllegalArgumentException("accepted claim must not expose an existing digest");
        }
        if (disposition != InboxClaimDisposition.ACCEPTED && existingPayloadSha256.isEmpty()) {
            throw new IllegalArgumentException("duplicate/conflict result must include existing digest");
        }
        existingPayloadSha256.ifPresent(digest -> {
            if (!digest.matches("(?i)[0-9a-f]{64}")) {
                throw new IllegalArgumentException("existing payload digest must be SHA-256 hex");
            }
        });
    }

    public boolean mayBeginProcessing() {
        return disposition == InboxClaimDisposition.ACCEPTED;
    }
}
