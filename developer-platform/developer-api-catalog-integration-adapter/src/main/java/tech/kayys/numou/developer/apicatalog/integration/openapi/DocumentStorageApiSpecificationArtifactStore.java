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

    private static String safe(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException("Unsafe API artifact path segment");
        }
        return value;
    }
}
