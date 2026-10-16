package tech.kayys.syirkah.accounting.application.document;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe in-memory storage implementation for tests. */
public final class InMemoryDocumentStorage implements DocumentStorage {

    private final Map<String, byte[]> store = new ConcurrentHashMap<>();

    @Override
    public String store(String key, byte[] content) {
        store.put(key, content);
        return key;
    }

    @Override
    public Optional<byte[]> retrieve(String key) {
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public boolean exists(String key) {
        return store.containsKey(key);
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }
}
