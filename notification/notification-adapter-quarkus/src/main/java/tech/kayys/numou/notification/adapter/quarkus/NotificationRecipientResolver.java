package tech.kayys.numou.notification.adapter.quarkus;

import java.util.Optional;
import java.util.UUID;

/**
 * Explicit identity bridge from a verified security principal to a notification
 * recipient. Implementations must enforce tenant scope and active identity
 * association; principal IDs must not be assumed to equal recipient IDs.
 */
public interface NotificationRecipientResolver {
    Optional<UUID> resolveRecipient(UUID tenantId, UUID principalId);
}
