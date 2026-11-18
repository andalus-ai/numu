package tech.kayys.numou.security.vault.spi;

import java.time.Instant;
import java.util.UUID;

/** Metadata-only event; deliberately excludes plaintext, ciphertext and key material. */
public record VaultAuditOutboxEvent(UUID eventId, UUID tenantId, UUID vaultId, UUID secretId,
                                    int secretVersion, String actor, String eventType,
                                    String details, Instant occurredAt, int attempts) {
    public VaultAuditOutboxEvent {
        if (eventId == null || tenantId == null || vaultId == null || secretId == null
                || secretVersion < 1 || actor == null || eventType == null || details == null
                || occurredAt == null || attempts < 1) {
            throw new IllegalArgumentException("invalid vault audit outbox event");
        }
    }
}
