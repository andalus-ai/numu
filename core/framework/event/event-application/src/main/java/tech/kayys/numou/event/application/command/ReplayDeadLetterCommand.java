package tech.kayys.numou.event.application.command;

import java.util.UUID;

/**
 * Explicit operator request to replay one already-inspected dead-letter
 * record. The source payload and event type are supplied by a trusted DLQ
 * reader adapter, not by an unauthenticated public request body.
 */
public record ReplayDeadLetterCommand(
        String deadLetterRecordId,
        UUID tenantId,
        String actorId,
        String reason,
        String eventType,
        String payload) {

    public ReplayDeadLetterCommand {
        require(deadLetterRecordId, "deadLetterRecordId");
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        require(actorId, "actorId");
        require(reason, "reason");
        require(eventType, "eventType");
        require(payload, "payload");
        if (reason.length() > 1000) throw new IllegalArgumentException("reason exceeds 1000 characters");
        if (payload.length() > 1_048_576) throw new IllegalArgumentException("DLQ payload exceeds 1 MiB");
        if (!eventType.matches("[A-Za-z0-9._-]+") || eventType.equals(".") || eventType.equals("..")) {
            throw new IllegalArgumentException("eventType is invalid");
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
