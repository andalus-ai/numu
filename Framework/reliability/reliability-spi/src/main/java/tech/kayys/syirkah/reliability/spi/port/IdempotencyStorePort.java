package tech.kayys.syirkah.reliability.spi.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.reliability.idempotency.IdempotencyOutcome;

/**
 * Claims idempotency keys for write operations (base01.md §P1-11, §P1-15).
 *
 * <p>Both event consumption and caller-facing writes use this, so "the same
 * request submitted twice" behaves identically in both paths.
 *
 * <p><b>Changed:</b> added {@link #release}. Without it, an operation that fails
 * after claiming its key leaves a claim with no result, and every client retry
 * is then treated as a duplicate of work that never completed.
 */
public interface IdempotencyStorePort {

    /** Atomically claims the key. Exactly one concurrent caller gets {@code claimed == true}. */
    Uni<IdempotencyOutcome> claim(String key, String scope);

    Uni<Void> recordResult(String key, String scope, String resultRef);

    /** Drops a claim that has no recorded result so the client may retry. A no-op if a result exists. */
    Uni<Void> release(String key, String scope);
}
