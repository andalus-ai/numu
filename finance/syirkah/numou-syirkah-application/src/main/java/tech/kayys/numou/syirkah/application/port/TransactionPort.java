package tech.kayys.numou.numouapplication.port;

import java.util.function.Supplier;

/**
 * Runs a unit of work atomically (repository save + journal posting + outbox).
 */
public interface TransactionPort {
    <T> T inTransaction(Supplier<T> work);
}
