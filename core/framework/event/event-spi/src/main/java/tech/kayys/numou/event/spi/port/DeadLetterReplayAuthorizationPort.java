package tech.kayys.numou.event.spi.port;

import io.smallrye.mutiny.Uni;

import java.util.UUID;

/**
 * Application-owned authorization hook for dead-letter inspection/replay.
 * Implementations must authorize the actor for the supplied tenant and
 * operation; possession of a DLQ record identifier is not authorization.
 */
public interface DeadLetterReplayAuthorizationPort {

    Uni<Void> authorizeReplay(UUID tenantId, String actorId, String deadLetterRecordId);
}
