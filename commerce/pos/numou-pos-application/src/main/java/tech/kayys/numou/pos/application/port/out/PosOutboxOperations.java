package tech.kayys.numou.pos.application.port.out;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Operational queries and tenant-scoped dead-letter recovery. */
public interface PosOutboxOperations {
    Map<String, Long> countByDeliveryStatus();
    boolean replayDeadLetter(UUID tenantId, UUID eventId, String actor, String reason, Instant replayedAt);
}
