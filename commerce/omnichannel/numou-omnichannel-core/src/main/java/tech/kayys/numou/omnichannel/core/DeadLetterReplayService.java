package tech.kayys.numou.omnichannel.core;

import java.time.Clock;
import java.util.Objects;

/**
 * Authorization boundary for operator-triggered replay. Bind this service to
 * the host application's authenticated principal and RBAC implementation.
 */
public final class DeadLetterReplayService {
    private final DeadLetterOperations operations;
    private final DeadLetterReplayAuthorizer authorizer;
    private final Clock clock;

    public DeadLetterReplayService(
            DeadLetterOperations operations,
            DeadLetterReplayAuthorizer authorizer,
            Clock clock) {
        this.operations = Objects.requireNonNull(operations, "operations must not be null");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public DeadLetterReplayResult replay(InboundEventKey key, String actorId, String reason) {
        Objects.requireNonNull(key, "key must not be null");
        String actor = required(actorId, "actorId", 200);
        String safeReason = required(reason, "reason", 500);
        if (!authorizer.mayReplay(actor, key)) {
            throw new SecurityException("Not authorized to replay this inbound event");
        }
        return operations.replay(key, actor, safeReason, clock.instant());
    }

    private static String required(String value, String field, int max) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(field + " must be non-blank and at most " + max + " characters");
        }
        return normalized;
    }
}
