package tech.kayys.syirkah.accounting.application.document;

import java.io.InputStream;
import java.util.Optional;

/** Pluggable SPI for binary document storage (S3, local filesystem, in-memory). */
public interface DocumentStorage {
    String store(String key, byte[] content);
    Optional<byte[]> retrieve(String key);
    boolean exists(String key);
    void delete(String key);
}
