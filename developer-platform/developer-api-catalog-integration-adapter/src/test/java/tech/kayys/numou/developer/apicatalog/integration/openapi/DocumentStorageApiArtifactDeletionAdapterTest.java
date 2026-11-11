package tech.kayys.numou.developer.apicatalog.integration.openapi;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.documents.storage.spi.DocumentStorage;
import tech.kayys.numou.documents.storage.spi.StorageKey;
import tech.kayys.numou.documents.storage.spi.StoredObjectMetadata;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

class DocumentStorageApiArtifactDeletionAdapterTest {
    private static final String SHA = "a".repeat(64);

    @Test
    void verifiesTenantKeyAndDigestBeforeDeleting() {
        TenantId tenant = TenantId.generate();
        StorageKey expectedKey = new StorageKey(tenant.value().toString(),
                "api-catalog/orders/1.0.0/" + SHA + ".openapi");
        FakeStorage storage = new FakeStorage();
        storage.metadata.set(new StoredObjectMetadata(expectedKey, 128, "application/yaml",
                SHA, Instant.parse("2026-11-01T00:00:00Z"), null));

        new DocumentStorageApiArtifactDeletionAdapter(storage)
                .delete(tenant, "s3://api-catalog/orders/1.0.0/" + SHA + ".openapi", SHA)
                .await().indefinitely();

        assertThat(storage.deleted.get()).isTrue();
        assertThat(storage.deletedKey.get().tenantId()).isEqualTo(tenant.value().toString());
        assertThat(storage.deletedKey.get().objectId()).isEqualTo(expectedKey.objectId());
    }

    @Test
    void treatsAlreadyMissingObjectAsSuccessfulRetry() {
        FakeStorage storage = new FakeStorage();
        TenantId tenant = TenantId.generate();

        assertThatCode(() -> new DocumentStorageApiArtifactDeletionAdapter(storage)
                .delete(tenant, "rustfs://api-catalog/orders/1.0.0/" + SHA + ".openapi", SHA)
                .await().indefinitely()).doesNotThrowAnyException();
        assertThat(storage.deleted.get()).isFalse();
    }

    @Test
    void refusesDigestMismatchAndUnsafeReferenceWithoutDeleting() {
        FakeStorage storage = new FakeStorage();
        TenantId tenant = TenantId.generate();
        StorageKey key = new StorageKey(tenant.value().toString(),
                "api-catalog/orders/1.0.0/" + SHA + ".openapi");
        storage.metadata.set(new StoredObjectMetadata(key, 128, "application/yaml",
                "b".repeat(64), Instant.now(), null));

        assertThatThrownBy(() -> new DocumentStorageApiArtifactDeletionAdapter(storage)
                .delete(tenant, "s3://api-catalog/orders/1.0.0/" + SHA + ".openapi", SHA)
                .await().indefinitely()).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new DocumentStorageApiArtifactDeletionAdapter(storage)
                .delete(tenant, "s3://other-bucket/orders/1.0.0/" + SHA + ".openapi", SHA)
                .await().indefinitely()).isInstanceOf(RuntimeException.class);
        assertThat(storage.deleted.get()).isFalse();
    }

    private static final class FakeStorage implements DocumentStorage {
        private final AtomicReference<StoredObjectMetadata> metadata = new AtomicReference<>();
        private final AtomicReference<StorageKey> deletedKey = new AtomicReference<>();
        private final AtomicBoolean deleted = new AtomicBoolean();

        @Override public StoredObjectMetadata put(StorageKey key, InputStream content, long length,
                                                   String type, String sha) {
            throw new UnsupportedOperationException();
        }
        @Override public Optional<StoredObject> get(StorageKey key) { return Optional.empty(); }
        @Override public Optional<StoredObjectMetadata> head(StorageKey key) {
            StoredObjectMetadata value = metadata.get();
            if (value == null || !value.key().tenantId().equals(key.tenantId())
                    || !value.key().objectId().equals(key.objectId())) return Optional.empty();
            return Optional.of(value);
        }
        @Override public boolean delete(StorageKey key) throws IOException {
            deletedKey.set(key);
            deleted.set(true);
            metadata.set(null);
            return true;
        }
    }
}
