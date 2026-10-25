package tech.kayys.numou.documents.storage.spi;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Provider-neutral metadata for a stored binary object. */
public final class StoredObjectMetadata {
    private final StorageKey key;
    private final long contentLength;
    private final String contentType;
    private final String sha256;
    private final Instant lastModified;
    private final String providerVersion;

    public StoredObjectMetadata(StorageKey key, long contentLength, String contentType,
                                String sha256, Instant lastModified, String providerVersion) {
        this.key = Objects.requireNonNull(key, "key");
        if (contentLength < 0) {
            throw new IllegalArgumentException("contentLength must be non-negative");
        }
        this.contentLength = contentLength;
        this.contentType = normalize(contentType);
        this.sha256 = normalize(sha256);
        this.lastModified = lastModified;
        this.providerVersion = normalize(providerVersion);
    }

    public StorageKey key() { return key; }
    public long contentLength() { return contentLength; }
    public Optional<String> contentType() { return Optional.ofNullable(contentType); }
    public Optional<String> sha256() { return Optional.ofNullable(sha256); }
    public Optional<Instant> lastModified() { return Optional.ofNullable(lastModified); }
    public Optional<String> providerVersion() { return Optional.ofNullable(providerVersion); }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
