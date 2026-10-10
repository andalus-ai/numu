package tech.kayys.syirkah.reservation.application;

import io.smallrye.mutiny.Uni;

import java.util.function.Supplier;

/**
 * Re-runs an optimistic-locking command when it detects a concurrency conflict.
 */
public final class ConflictRetry {

    private ConflictRetry() { }

    public static <T> Uni<T> run(int maxAttempts, Supplier<Uni<T>> work) {
        return attempt(1, maxAttempts, work);
    }

    private static <T> Uni<T> attempt(int n, int max, Supplier<Uni<T>> work) {
        return Uni.createFrom().<T>deferred(work::get).onFailure().recoverWithUni(failure ->
                isConflict(failure) && n < max
                        ? attempt(n + 1, max, work)
                        : Uni.<T>createFrom().failure(failure));
    }

    private static boolean isConflict(Throwable t) {
        return t != null && (t.getClass().getSimpleName().contains("ConcurrencyConflict")
                || t.getMessage() != null && t.getMessage().toLowerCase().contains("concurren"));
    }
}
