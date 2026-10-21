package tech.kayys.numou.numouapplication.ops;

import tech.kayys.numou.numoudomain.SyirkahId;

import java.time.Instant;
import java.util.UUID;

/**
 * One stored event awaiting delivery. {@code payload} is the JSON body;
 * consumers de-duplicate on {@code eventId}.
 */
public record OutboxRecord(long sequence, UUID eventId, String eventType, SyirkahId syirkahId, Instant occurredAt,
        String payload) {
}
