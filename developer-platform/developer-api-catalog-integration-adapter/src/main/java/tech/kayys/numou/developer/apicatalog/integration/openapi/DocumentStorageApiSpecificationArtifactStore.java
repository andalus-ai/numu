package tech.kayys.numou.developer.apicatalog.integration.openapi;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.developer.apicatalog.application.port.ApiSpecificationArtifactStore;
import tech.kayys.numou.documents.storage.spi.DocumentStorage;
import tech.kayys.numou.documents.storage.spi.StorageKey;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;

/** Stores immutable, content-addressed OpenAPI artifacts using Numou's shared storage SPI. */
@ApplicationScoped
public final class DocumentStorageApiSpecificationArtifactStore implements ApiSpecificationArtifactStore {
    private final DocumentStorage storage;

    @Inject
    public DocumentStorageApiSpecificationArtifactStore(DocumentStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage cannot be null");
    }

    @Override
    public Uni<UploadedApiSpecificationArtifact> store(TenantId tenant, String productId, String version,
                                                       byte[] content, String contentType, String sha256) {
        Objects.requireNonNull(tenant, "tenant cannot be null");
        Objects.requireNonNull(content, "content cannot be null");
        String objectId = "api-catalog/" + safe(productId) + "/" + safe(version) + "/" + sha256 + ".openapi";
        return Uni.createFrom().item(() -> {
            try {
                var metadata = storage.put(new StorageKey(tenant.value().toString(), objectId),
                        new ByteArrayInputStream(content), content.length, contentType, sha256);
                if (!metadata.sha256().map(sha256::equalsIgnoreCase).orElse(false)
                        || metadata.contentLength() != content.length) {
                    throw new IllegalStateException("Stored OpenAPI artifact metadata failed integrity verification");
                }
                return new UploadedApiSpecificationArtifact("s3://api-catalog/" + safe(productId) + "/"
                        + safe(version) + "/" + sha256 + ".openapi", sha256, metadata.contentLength(), contentType);
            } catch (IOException failure) {
                throw new IllegalStateException("Unable to persist OpenAPI specification artifact", failure);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<RetrievedApiSpecificationArtifact> retrieve(TenantId tenant, String artifactReference,
                                                            String expectedSha256) {
        Objects.requireNonNull(tenant, "tenant cannot be null");
        Objects.requireNonNull(expectedSha256, "expectedSha256 cannot be null");
        if (!expectedSha256.matches("[a-fA-F0-9]{64}")) {
            return Uni.createFrom().failure(new IllegalArgumentException("Invalid expected SHA-256 digest"));
        }
        final StorageKey key;
        try {
            java.net.URI uri = java.net.URI.create(artifactReference);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            if (!(scheme.equals("s3") || scheme.equals("rustfs")) || uri.getHost() == null
                    || uri.getHost().isBlank() || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || uri.getPort() != -1 || uri.getRawPath() == null
                    || uri.getRawPath().length() < 2 || uri.getRawPath().contains("%2f")
                    || uri.getRawPath().contains("%2F")) {
                throw new IllegalArgumentException("Unsupported or unsafe artifact reference");
            }
            key = new StorageKey(tenant.value().toString(), uri.getHost() + uri.getRawPath());
        } catch (RuntimeException invalid) {
            return Uni.createFrom().failure(invalid);
        }
        return Uni.createFrom().item(() -> {
            try {
                var stored = storage.get(key).orElseThrow(() ->
                        new IllegalArgumentException("OpenAPI artifact does not exist for provider tenant"));
                try (stored) {
                    var metadata = stored.metadata();
                    if (!metadata.key().tenantId().equals(key.tenantId())
                            || !metadata.key().objectId().equals(key.objectId())
                            || metadata.contentLength() < 1 || metadata.contentLength() > 10 * 1024 * 1024) {
                        throw new IllegalStateException("Stored artifact metadata does not match requested tenant key");
                    }
                    byte[] bytes = stored.content().readNBytes(10 * 1024 * 1024 + 1);
                    if (bytes.length > 10 * 1024 * 1024 || bytes.length != metadata.contentLength()) {
                        throw new IllegalStateException("Stored artifact length is invalid");
                    }
                    String actual = java.util.HexFormat.of().formatHex(
                            java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
                    if (!java.security.MessageDigest.isEqual(
                            expectedSha256.toLowerCase(java.util.Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                            actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                        throw new IllegalStateException("Stored artifact SHA-256 does not match catalog metadata");
                    }
                    if (metadata.sha256().isPresent() && !metadata.sha256().get().equalsIgnoreCase(actual)) {
                        throw new IllegalStateException("Stored artifact checksum metadata is inconsistent");
                    }
                    return new RetrievedApiSpecificationArtifact(bytes, actual,
                            metadata.contentType().orElse("application/octet-stream"), bytes.length);
                }
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Unable to retrieve OpenAPI specification artifact", failure);
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-256 is unavailable", impossible);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private static String safe(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException("Unsafe API artifact path segment");
        }
        return value;
    }
}
