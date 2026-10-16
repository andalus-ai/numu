package tech.kayys.numou.foundation.application.transaction;

import java.util.Objects;
import java.util.function.Supplier;

/** Runs the supplier with no ambient transaction (tests / in-memory). */
public final class DirectTransactionRunner implements TransactionRunner {

    @Override
    public <T> T execute(Supplier<T> operation) {
        return Objects.requireNonNull(operation, "operation").get();
    }
}
