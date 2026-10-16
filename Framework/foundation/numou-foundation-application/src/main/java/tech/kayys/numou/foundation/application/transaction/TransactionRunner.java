package tech.kayys.numou.foundation.application.transaction;

import java.util.function.Supplier;

/**
 * Synchronous unit-of-work boundary (product02.md).
 * Prefer {@link UnitOfWork} for reactive handlers; use this for
 * blocking orchestration and tests.
 */
public interface TransactionRunner {

    <T> T execute(Supplier<T> operation);
}
