package tech.kayys.numou.reliability.application.idempotency;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.reliability.spi.port.IdempotencyStorePort;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Runs a write at most once per (scope, key) and replays its result for repeats.
 *
 * <p>Typical use: the {@code Idempotency-Key} header on {@code POST /reservations}, so a
 * client retrying after a network timeout does not create a second booking.
 *
 * <p>Failure handling: if the business work fails the claim is <em>released</em> so a retry
 * can run. If the work succeeds but encoding or persisting its result fails, the claim is
 * deliberately retained: the outcome is uncertain and re-executing could duplicate a side
 * effect. A duplicate without a recorded result gets {@link DuplicateRequestException} (HTTP 409)
 * and requires reconciliation rather than automatic re-execution.
 */
public final class IdempotentExecutor {

    private final IdempotencyStorePort store;

    public IdempotentExecutor(IdempotencyStorePort store) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
    }

    public <R> Uni<R> execute(String key, String scope, Supplier<Uni<R>> work,
                              Function<R, String> encodeResult, Function<String, R> decodeResult) {
        if (key == null || key.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("idempotency key is required"));
        }
        return store.claim(key, scope).flatMap(outcome -> {
            if (outcome.isDuplicate()) {
                return outcome.resultRef().isEmpty()
                        ? Uni.<R>createFrom().failure(new DuplicateRequestException(key))
                        : Uni.createFrom().item(() -> decodeResult.apply(outcome.resultRef()));
            }
            return Uni.createFrom().<R>deferred(() -> work.get())
                    // Only a failed business operation may release the claim. Once work has
                    // succeeded, a result-store/encoding failure must leave the claim in place:
                    // retrying the business operation could otherwise duplicate its side effect.
                    .onFailure().recoverWithUni(failure ->
                            store.release(key, scope)
                                    .onFailure().recoverWithItem((Void) null)
                                    .flatMap(ignored -> Uni.<R>createFrom().failure(failure)))
                    .flatMap(result -> store.recordResult(key, scope, encodeResult.apply(result)).replaceWith(result));
        });
    }
}
