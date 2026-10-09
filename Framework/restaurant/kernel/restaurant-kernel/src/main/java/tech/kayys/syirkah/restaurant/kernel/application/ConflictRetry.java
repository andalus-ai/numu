package tech.kayys.syirkah.restaurant.kernel.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.restaurant.kernel.domain.ConcurrencyConflictException;

import java.util.function.Supplier;

/**
 * Re-runs a whole command when it loses an optimistic-lock race. The work must reload its aggregates on every
 * attempt (which is why it is a {@link Supplier}, not a Uni): a retry that reused stale state would just lose again.
 */
public final class ConflictRetry {

    private ConflictRetry() { }

    public static <T> Uni<T> run(int maxAttempts, Supplier<Uni<T>> work) {
        return attempt(1, maxAttempts, work);
    }

    private static <T> Uni<T> attempt(int n, int max, Supplier<Uni<T>> work) {
        return Uni.createFrom().<T>deferred(() -> work.get()).onFailure().recoverWithUni(failure ->
                failure instanceof ConcurrencyConflictException && n < max
                        ? attempt(n + 1, max, work)
                        : Uni.<T>createFrom().failure(failure));
    }
}
