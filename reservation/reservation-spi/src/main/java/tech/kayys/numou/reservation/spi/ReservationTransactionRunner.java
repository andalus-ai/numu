package tech.kayys.numou.reservation.spi;

import io.smallrye.mutiny.Uni;
import java.util.function.Function;

/** Executes a reservation command with repositories and event sink sharing one transaction. */
public interface ReservationTransactionRunner {
    <T> Uni<T> execute(Function<ReservationTransactionContext, Uni<T>> work);
}
