package tech.kayys.numou.ecosystem.application.port;

import io.smallrye.mutiny.Uni;

import java.util.Objects;
import java.util.function.Function;

/**
 * Executes an Ecosystem command against one transaction-scoped context.
 *
 * <p>The implementation must commit only when the returned Uni completes
 * successfully and roll back when it fails or the callback returns null.
 * A successful Result containing a business validation failure is a normal
 * command outcome; callers should return it without writing state.</p>
 */
public interface EcosystemTransactionRunner {
    <T> Uni<T> inTransaction(Function<EcosystemTransaction, Uni<T>> work);

    static <T> Uni<T> invoke(
            Function<EcosystemTransaction, Uni<T>> work,
            EcosystemTransaction transaction) {
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(transaction, "transaction");
        Uni<T> result = work.apply(transaction);
        if (result == null) {
            return Uni.createFrom().failure(
                    new IllegalStateException("Ecosystem transaction callback returned null Uni"));
        }
        return result;
    }
}
