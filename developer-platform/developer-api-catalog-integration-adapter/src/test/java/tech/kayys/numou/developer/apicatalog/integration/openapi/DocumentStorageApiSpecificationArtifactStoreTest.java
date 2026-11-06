package tech.kayys.numou.developer.apicatalog.integration.openapi;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.documents.storage.spi.DocumentStorage;
import tech.kayys.numou.documents.storage.spi.StorageKey;
import tech.kayys.numou.documents.storage.spi.StoredObjectMetadata;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentStorageApiSpecificationArtifactStoreTest {
    @Test
    void storesAndRetrievesContentAddressedArtifactUnderProviderTenant() throws Exception {
        CapturingStorage storage = new CapturingStorage();
        var adapter = new DocumentStorageApiSpecificationArtifactStore(storage);
        var tenant = TenantId.generate();
        byte[] bytes = "openapi: 3.1.0\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String digest = sha256(bytes);

        var uploaded = adapter.store(tenant, UUID.randomUUID().toString(), "1.2.3",
                bytes, "text/yaml", digest).await().indefinitely();

        assertThat(storage.key.tenantId()).isEqualTo(tenant.value().toString());
        assertThat(storage.key.objectId()).startsWith("api-catalog/");
        assertThat(storage.key.objectId()).endsWith(digest + ".openapi");
        assertThat(uploaded.artifactReference()).startsWith("s3://api-catalog/");
        assertThat(uploaded.sha256()).isEqualTo(digest);
        assertThat(uploaded.contentLength()).isEqualTo(bytes.length);

        var downloaded = adapter.retrieve(tenant, uploaded.artifactReference(), digest).await().indefinitely();
        assertThat(downloaded.content()).containsExactly(bytes);
        assertThat(downloaded.sha256()).isEqualTo(digest);
        assertThat(downloaded.contentType()).isEqualTo("text/yaml");
        assertThat(downloaded.contentLength()).isEqualTo(bytes.length);

        var anotherTenant = TenantId.generate();
        assertThatThrownBy(() -> adapter.retrieve(anotherTenant, uploaded.artifactReference(), digest)
                .await().indefinitely())
                .hasMessageContaining("does not exist for provider tenant");
    }

    @Test
    void rejectsRetrievalWhenCatalogDigestDoesNotMatchStoredBytes() throws Exception {
        CapturingStorage storage = new CapturingStorage();
        var adapter = new DocumentStorageApiSpecificationArtifactStore(storage);
        var tenant = TenantId.generate();
        byte[] bytes = "openapi: 3.1.0\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String digest = sha256(bytes);
        var uploaded = adapter.store(tenant, UUID.randomUUID().toString(), "1.2.3",
                bytes, "text/yaml", digest).await().indefinitely();

        assertThatThrownBy(() -> adapter.retrieve(tenant, uploaded.artifactReference(),
                "0".repeat(64)).await().indefinitely())
                .hasMessageContaining("SHA-256");
    }

    @Test
    void rejectsArtifactReferenceThatAttemptsToAddressAnotherTenantOrUnsafePath() {
        var adapter = new DocumentStorageApiSpecificationArtifactStore(new CapturingStorage());
        var tenant = TenantId.generate();
        assertThatThrownBy(() -> adapter.retrieve(tenant, "s3://api-catalog/../other/secret",
                "0".repeat(64)).await().indefinitely())
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static final class CapturingStorage implements DocumentStorage {
        private StorageKey key;
        private byte[] stored;
        private StoredObjectMetadata metadata;

        @Override
        public StoredObjectMetadata put(StorageKey key, InputStream content, long length,
                                        String contentType, String sha256) throws IOException {
            this.key = key;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            content.transferTo(bytes);
            stored = bytes.toByteArray();
            metadata = new StoredObjectMetadata(key, stored.length, contentType, sha256, Instant.now(), null);
            return metadata;
        }

        @Override
        public Optional<StoredObject> get(StorageKey requested) {
            if (key == null || !key.tenantId().equals(requested.tenantId())
                    || !key.objectId().equals(requested.objectId())) return Optional.empty();
            return Optional.of(new StoredObject() {
                @Override public StoredObjectMetadata metadata() { return metadata; }
                @Override public InputStream content() { return new ByteArrayInputStream(stored); }
                @Override public void close() { }
            });
        }

        @Override public Optional<StoredObjectMetadata> head(StorageKey requested) {
            return get(requested).map(StoredObject::metadata);
        }
        @Override public boolean delete(StorageKey requested) { return false; }
    }
}
