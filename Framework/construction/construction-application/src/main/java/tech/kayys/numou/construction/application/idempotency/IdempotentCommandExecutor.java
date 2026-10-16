package tech.kayys.numou.construction.application.idempotency;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.construction.spi.idempotency.CommandIdempotencyStore;
import tech.kayys.numou.construction.spi.idempotency.TransactionalCommandContext;
import tech.kayys.numou.construction.spi.idempotency.TransactionalCommandRunner;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;

/**
 * Executes a command and persists its response atomically with business writes.
 * The runner and context adapter are responsible for sharing one DB transaction.
 */
public final class IdempotentCommandExecutor {
    private final TransactionalCommandRunner runner;
    private final CommandOutcomeCodec codec;

    public IdempotentCommandExecutor(TransactionalCommandRunner runner, CommandOutcomeCodec codec) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public <T> Uni<T> execute(String tenantId, String key, String operation,
                              String fingerprint, Instant now, Class<T> resultType,
                              Function<TransactionalCommandContext, Uni<T>> action) {
        requireText(tenantId, "tenantId");
        requireText(key, "key");
        requireText(operation, "operation");
        requireText(fingerprint, "fingerprint");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(resultType, "resultType");
        Objects.requireNonNull(action, "action");

        return runner.inTransaction(context -> context.reserve(new CommandIdempotencyStore.Reservation(
                        tenantId, key, operation, fingerprint, now))
                .flatMap(reservation -> switch (reservation.status()) {
                    case RESERVED -> action.apply(context).flatMap(result -> {
                        String responseJson = codec.encode(result);
                        return context.complete(tenantId, key, fingerprint, responseJson, now)
                                .flatMap(completed -> completed
                                        ? Uni.createFrom().item(result)
                                        : Uni.createFrom().failure(new IdempotencyConflictException(
                                                "Could not complete idempotency record; transaction must roll back")));
                    });
                    case KEY_REUSED_WITH_DIFFERENT_REQUEST -> Uni.createFrom().failure(
                            new IdempotencyConflictException("Idempotency key was reused for a different request"));
                    case EXISTING_MATCH -> {
                        CommandIdempotencyStore.StoredOutcome outcome = reservation.existing()
                                .orElseThrow(() -> new IdempotencyConflictException(
                                        "Existing reservation did not include its stored outcome"));
                        if (!operation.equals(outcome.operation())
                                || !fingerprint.equals(outcome.requestFingerprint())) {
                            yield Uni.createFrom().failure(new IdempotencyConflictException(
                                    "Stored idempotency request does not match"));
                        }
                        if (!"COMPLETED".equals(outcome.state()) || outcome.responseJson() == null) {
                            yield Uni.createFrom().failure(new IdempotencyInProgressException(
                                    "Matching command exists but has no completed response"));
                        }
                        yield Uni.createFrom().item(codec.decode(outcome.responseJson(), resultType));
                    }
                }));
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }

    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) { super(message); }
    }

    public static class IdempotencyInProgressException extends RuntimeException {
        public IdempotencyInProgressException(String message) { super(message); }
    }
}
