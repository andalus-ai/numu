package tech.kayys.numou.pos.application;

import tech.kayys.numou.pos.application.port.out.PosOutboxOperations;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Explicit dead-letter replay with an auditable operator identity and reason. */
public final class ReplayPosDeadLetter {
    private final PosOutboxOperations operations;
    private final Clock clock;
    public ReplayPosDeadLetter(PosOutboxOperations operations, Clock clock) {
        this.operations = Objects.requireNonNull(operations);
        this.clock = Objects.requireNonNull(clock);
    }
    public boolean execute(UUID tenantId, UUID eventId, String actor, String reason) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(eventId, "eventId");
        return operations.replayDeadLetter(tenantId, eventId, required(actor, "actor"),
                required(reason, "reason"), clock.instant());
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        String normalized = value.trim();
        if (normalized.length() > 500) throw new IllegalArgumentException(name + " must not exceed 500 characters");
        return normalized;
    }
}
