package tech.kayys.numou.scope.application;

import tech.kayys.numou.scope.domain.BreakdownStructureId;
import tech.kayys.numou.scope.spi.BreakdownStructureWriteLock;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Single-process lock implementation useful for tests and single-instance
 * deployments. Clustered deployments should bind a distributed implementation.
 */
public final class InProcessBreakdownStructureWriteLock implements BreakdownStructureWriteLock {
    private final ConcurrentHashMap<BreakdownStructureId, CompletableFuture<Void>> tails =
            new ConcurrentHashMap<>();

    @Override
    public synchronized <T> CompletionStage<T> withLock(
            BreakdownStructureId structureId,
            Supplier<CompletionStage<T>> operation) {
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        Objects.requireNonNull(operation, "Operation cannot be null");

        CompletableFuture<Void> previous = tails.getOrDefault(
                structureId, CompletableFuture.completedFuture(null));
        CompletableFuture<T> result = previous.handle((ignored, failure) -> null)
                .thenCompose(ignored -> {
                    try {
                        return Objects.requireNonNull(operation.get(), "Operation stage cannot be null");
                    } catch (Throwable failure) {
                        return CompletableFuture.failedFuture(failure);
                    }
                }).toCompletableFuture();
        CompletableFuture<Void> tail = result.handle((ignored, failure) -> null);
        tails.put(structureId, tail);
        tail.whenComplete((ignored, failure) -> tails.remove(structureId, tail));
        return result;
    }
}
