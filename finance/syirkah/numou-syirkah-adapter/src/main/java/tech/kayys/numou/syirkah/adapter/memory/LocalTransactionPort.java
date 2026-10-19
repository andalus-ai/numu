package tech.kayys.numou.syirkah.adapter.memory;

import tech.kayys.numou.syirkah.application.port.TransactionPort;

import java.util.function.Supplier;

/** Serializes units of work with one lock; stand-in for a real transaction manager in single-node use. */
public class LocalTransactionPort implements TransactionPort {

    private final Object lock = new Object();

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        synchronized (lock) {
            return work.get();
        }
    }
}
