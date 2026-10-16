package tech.kayys.syirkah.accounting.application.hardening;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ensures commands tagged with an idempotency key execute at most once.
 */
public final class IdempotencyGuard {

    private final Set<String> processedKeys = ConcurrentHashMap.newKeySet();

    /**
     * @return true if key was acquired (first time); false if key has already been executed.
     */
    public boolean acquire(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return true;
        return processedKeys.add(idempotencyKey);
    }

    public boolean isProcessed(String idempotencyKey) {
        return processedKeys.contains(idempotencyKey);
    }

    public void clear() { processedKeys.clear(); }
}
