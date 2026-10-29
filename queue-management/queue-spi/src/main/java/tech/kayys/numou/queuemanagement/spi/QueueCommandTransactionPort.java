package tech.kayys.numou.queuemanagement.spi;

import java.util.function.Supplier;

/**
 * Transaction boundary for queue write commands.
 *
 * Implementations must atomically commit the business mutation, append-only audit record,
 * durable outbox event, and idempotency result for (tenant, operation scope, idempotency key).
 * A repeated completed command returns its recorded result; an in-flight duplicate must not
 * execute a second mutation. A failed transaction must not publish an event or retain a
 * completed idempotency result. Adapter implementations should reuse Numou Reliability
 * contracts where their transaction semantics permit; do not assume a separate claim/result
 * transaction is atomic with queue state.
 */
public interface QueueCommandTransactionPort {
    <T> T execute(QueueCommandContext context, String operationScope,
                  Supplier<QueueCommandResult<T>> work);
}