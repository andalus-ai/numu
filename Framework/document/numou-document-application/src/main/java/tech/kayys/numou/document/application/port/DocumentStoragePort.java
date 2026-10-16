package tech.kayys.syirkah.document.application.port;

import io.smallrye.mutiny.Uni;

import java.time.Instant;
import java.util.Map;

public interface DocumentStoragePort {
    Uni<UploadTarget> createUploadTarget(UploadTargetRequest request);

    Uni<StoredDocument> inspect(String storageKey);

    Uni<Void> delete(String storageKey);

    record UploadTarget(String storageKey, String uploadUrl, Instant expiresAt, Map<String, String> requiredHeaders) {
        public UploadTarget {
            requiredHeaders = requiredHeaders == null ? Map.of() : Map.copyOf(requiredHeaders);
        }

        public UploadTarget(String storageKey, String uploadUrl, Instant expiresAt) {
            this(storageKey, uploadUrl, expiresAt, Map.of());
        }
    }

    record UploadTargetRequest(
            String tenantId,
            String uploadSessionId,
            String fileName,
            String contentType,
            long expectedSize,
            String expectedSha256,
            Instant expiresAt
    ) {}

    record StoredDocument(long size, String contentType, String sha256) {}
}
