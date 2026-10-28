package tech.kayys.numou.notification.application.port;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Supplies the notification recipient for the current authenticated request.
 *
 * <p>Host applications must implement this port using their verified Numou
 * security context and an explicit principal-to-recipient mapping. Implementors
 * must never derive identity from request DTOs, query parameters, or unverified
 * headers. An empty result means there is no authenticated notification actor.
 */
public interface NotificationRequestContext {
    Optional<Actor> currentActor();

    record Actor(UUID tenantId, UUID recipientId) {
        public Actor {
            Objects.requireNonNull(tenantId, "tenantId is required");
            Objects.requireNonNull(recipientId, "recipientId is required");
        }
    }
}