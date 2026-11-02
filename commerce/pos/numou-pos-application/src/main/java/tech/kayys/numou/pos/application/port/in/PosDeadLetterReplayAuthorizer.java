package tech.kayys.numou.pos.application.port.in;

import java.util.UUID;

/**
 * Authorization policy supplied by the hosting application.
 *
 * Implementations must evaluate the authenticated actor's permission for the
 * requested tenant and event. The actor identifier must come from the host's
 * authenticated principal, never from an untrusted request body.
 */
@FunctionalInterface
public interface PosDeadLetterReplayAuthorizer {
    boolean mayReplay(UUID tenantId, UUID eventId, String actor);
}
