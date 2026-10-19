package tech.kayys.numou.construction.spi.idempotency;

import io.smallrye.mutiny.Uni;
import java.util.function.Function;

/** Runs the callback in one transaction and rolls back if the returned Uni fails. */
public interface TransactionalCommandRunner {
    <T> Uni<T> inTransaction(Function<TransactionalCommandContext, Uni<T>> work);
}
