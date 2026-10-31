package tech.kayys.numou.queuemanagement.spi;

import java.util.UUID;

/** Read-only tenant-scoped operational health query. Implementations must not return event payloads. */
public interface QueueOperationalHealthPort {
    QueueOperationalSnapshot snapshot(UUID tenantId);
}
