package tech.kayys.numou.event.application.replay;

import java.util.UUID;

/** Authorization must be derived from the authenticated principal, never a request-supplied actor identity. */
@FunctionalInterface
public interface DeadLetterReplayAuthorizer {
    boolean mayReplay(UUID tenantId, DeadLetterSourceKey source, String authenticatedActorId);
}
