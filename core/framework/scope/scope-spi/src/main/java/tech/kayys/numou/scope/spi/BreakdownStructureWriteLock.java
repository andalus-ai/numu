package tech.kayys.numou.scope.spi;

import tech.kayys.numou.scope.domain.BreakdownStructureId;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Serializes a mutation against other mutations of the same breakdown structure.
 *
 * <p>The lock must remain held until the supplied asynchronous operation has
 * completed, including exceptional completion. Production implementations
 * must coordinate across application instances (for example, with a database
 * advisory lock) when the service is deployed in a cluster.</p>
 */
public interface BreakdownStructureWriteLock {
    <T> CompletionStage<T> withLock(
            BreakdownStructureId structureId,
            Supplier<CompletionStage<T>> operation);
}
