package tech.kayys.numou.syirkah.application.port;

import java.util.function.Supplier;

/** Runs a unit of work atomically (repository save + journal posting + outbox). */
public interface TransactionPort {
    <T> T inTransaction(Supplier<T> work);
}
