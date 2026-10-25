package tech.kayys.numou.documents.storage.spi;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Binary-object storage port. Implementations must enforce tenant-scoped keys
 * and must not treat a storage key as proof of authorization.
 */
public interface DocumentStorage extends AutoCloseable {
    StoredObjectMetadata put(StorageKey key, InputStream content, long contentLength,
                            String contentType, String sha256) throws IOException;

    Optional<StoredObject> get(StorageKey key) throws IOException;

    Optional<StoredObjectMetadata> head(StorageKey key) throws IOException;

    boolean delete(StorageKey key) throws IOException;

    /** Release provider resources. Local implementations may use the no-op default. */
    @Override
    default void close() throws IOException {
        // No persistent resources owned by the generic contract.
    }

    interface StoredObject extends AutoCloseable {
        StoredObjectMetadata metadata();

        InputStream content();

        @Override
        void close() throws IOException;
    }
}
