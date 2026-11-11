package tech.kayys.numou.developer.apicatalog.integration.openapi;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactObjectDeletionPort;
import tech.kayys.numou.documents.storage.spi.DocumentStorage;
import tech.kayys.numou.documents.storage.spi.StorageKey;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/**
 * Deletes only catalog-owned, content-addressed objects. The tenant component always
 * comes from the trusted TenantId supplied by the tenant-scoped worker, never from the URI.
 */
@ApplicationScoped
public final class DocumentStorageApiArtifactDeletionAdapter implements ApiArtifactObjectDeletionPort {
    private final DocumentStorage storage;

    @Inject
    public DocumentStorageApiArtifactDeletionAdapter(DocumentStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage cannot be null");
    }

    @Override
    public Uni<Void> delete(TenantId tenant, String artifactReference, String expectedSha256) {
        Objects.requireNonNull(tenant, "tenant cannot be null");
        Objects.requireNonNull(expectedSha256, "expectedSha256 cannot be null");
        final StorageKey key;
        try {
            if (!expectedSha256.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("Invalid expected artifact SHA-256");
            }
            URI uri = URI.create(Objects.requireNonNull(artifactReference, "artifactReference cannot be null"));
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("s3") || scheme.equals("rustfs"))
                    || !"api-catalog".equals(uri.getHost())
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPort() != -1 || uri.getRawPath() == null
                    || !uri.getRawPath().matches("/[A-Za-z0-9][A-Za-z0-9._-]{0,127}/[A-Za-z0-9][A-Za-z0-9._-]{0,127}/[a-f0-9]{64}\\.openapi")) {
                throw new IllegalArgumentException("Unsupported or unsafe API artifact reference");
            }
            String digestInPath = uri.getRawPath().substring(uri.getRawPath().lastIndexOf('/') + 1,
                    uri.getRawPath().length() - ".openapi".length());
            if (!digestInPath.equals(expectedSha256)) {
                throw new IllegalArgumentException("Artifact reference digest differs from lifecycle registry");
            }
            key = new StorageKey(tenant.value().toString(), uri.getHost() + uri.getRawPath());
        } catch (RuntimeException invalid) {
            return Uni.createFrom().failure(invalid);
        }

        return Uni.createFrom().item(() -> {
            try {
                var metadata = storage.head(key);
                if (metadata.isEmpty()) return Boolean.TRUE; // Idempotent recovery after a prior successful delete.
                var stored = metadata.get();
                if (!stored.key().tenantId().equals(key.tenantId())
                        || !stored.key().objectId().equals(key.objectId())
                        || stored.contentLength() < 1
                        || stored.sha256().isEmpty()
                        || !stored.sha256().get().equalsIgnoreCase(expectedSha256)) {
                    throw new IllegalStateException("Stored artifact identity or checksum failed cleanup verification");
                }
                storage.delete(key); // false means another retry already removed it.
                return Boolean.TRUE;
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Unable to delete API specification artifact", failure);
            }
        }).replaceWithVoid().runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
