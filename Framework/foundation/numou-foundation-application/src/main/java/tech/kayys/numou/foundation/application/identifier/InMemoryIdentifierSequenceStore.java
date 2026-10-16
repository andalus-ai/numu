package tech.kayys.numou.foundation.application.identifier;

import io.smallrye.mutiny.Uni;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe in-memory sequence store implementation for testing and development environments.
 */
public class InMemoryIdentifierSequenceStore implements IdentifierSequenceStore {

    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public Uni<Long> allocateNext(SequenceKey key) {
        String compositeKey = key.namespace() + ":" + key.scopeKey() + ":" + key.periodKey();
        AtomicLong counter = counters.computeIfAbsent(compositeKey, k -> new AtomicLong(0));
        long nextVal = counter.incrementAndGet();
        return Uni.createFrom().item(nextVal);
    }

    public void reset() {
        counters.clear();
    }
}
