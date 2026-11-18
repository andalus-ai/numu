package tech.kayys.numou.pos.application;

import tech.kayys.numou.pos.application.port.out.PosDeadLetterReplayAuthorizer;
import tech.kayys.numou.pos.application.port.out.PosOutboxOperations;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Explicit, tenant-authorized dead-letter replay with an auditable operator identity and reason.
 *
 * This use case only requeues the original persisted outbox envelope. It does not
 * accept arbitrary payloads or expose a broker-management operation.
 */
public final class ReplayPosDeadLetter {
    private final PosOutboxOperations operations;
    private final PosDeadLetterReplayAuthorizer authorizer;
    private final Clock clock;

    public ReplayPosDeadLetter(
            PosOutboxOperations operations,
            PosDeadLetterReplayAuthorizer authorizer,
            Clock clock) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean execute(UUID tenantId, UUID eventId, String actor, String reason) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(eventId, "eventId");
        String authenticatedActor = required(actor, "actor");
        String safeReason = required(reason, "reason");

        if (!authorizer.mayReplay(tenantId, eventId, authenticatedActor)) {
            throw new SecurityException("Not authorized to replay this POS dead-letter event");
        }

        return operations.replayDeadLetter(
                tenantId, eventId, authenticatedActor, safeReason, clock.instant());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > 500) {
            throw new IllegalArgumentException(name + " must not exceed 500 characters");
        }
        return normalized;
    }
}
