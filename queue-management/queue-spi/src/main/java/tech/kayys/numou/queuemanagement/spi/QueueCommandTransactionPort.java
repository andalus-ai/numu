package tech.kayys.numou.queuemanagement.spi;

import java.util.function.Supplier;

/**
 * Transaction boundary for queue write commands.
 *
 * Implementations atomically commit the queue mutation, append-only audit records,
 * durable outbox events, and idempotency result for (tenant, operation scope, key).
 * The request hash is computed from normalized business arguments by the application
 * layer; it must exclude volatile metadata such as timestamps, correlation IDs, and
 * actor IDs unless those fields are explicitly part of the command's business meaning.
 *
 * A repeated completed command with the same hash returns the original typed result.
 * Reuse of the same key with a different hash must fail without executing the mutation.
 * The implementation must not commit IN_PROGRESS rows independently of the business
 * transaction, and a failed transaction must not retain a completed result or publish events.
 * Result payload encoding/decoding must be explicit and type-safe; arbitrary toString()
 * serialization is not a valid replay strategy.
 */
public interface QueueCommandTransactionPort {
    <T> T execute(QueueCommandContext context, String operationScope, String requestHash,
                  Supplier<QueueCommandResult<T>> work);
}
