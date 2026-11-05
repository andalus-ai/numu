package tech.kayys.numou.developer.apicatalog.integration.openapi;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.documents.storage.spi.DocumentStorage;
import tech.kayys.numou.documents.storage.spi.StorageKey;
import tech.kayys.numou.documents.storage.spi.StoredObjectMetadata;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentStorageApiSpecificationArtifactStoreTest {
    @Test
    void storesContentAddressedArtifactUnderProviderTenantAndReturnsValidatorCompatibleReference() {
        CapturingStorage storage = new CapturingStorage();
        var adapter = new DocumentStorageApiSpecificationArtifactStore(storage);
        var tenant = TenantId.generate();
        byte[] bytes = "{\\"openapi\\":\\"3.1.0\\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String digest = "e6b8c1f8c7b4b1c4b1e6e4d9d9b6c0e8f8b6a1d1f7c5a9a6b2c3d4e5f6071829";

        var result = adapter.store(tenant, UUID.randomUUID().toString(), "1.2.3",
                bytes, "application/json", digest).await().indefinitely();

        assertThat(storage.key.tenantId()).isEqualTo(tenant.value().toString());
        assertThat(storage.key.objectId()).startsWith("api-catalog/");
        assertThat(storage.key.objectId()).endsWith(digest + ".openapi");
        assertThat(result.artifactReference()).startsWith("s3://api-catalog/");
        assertThat(result.sha256()).isEqualTo(digest);
        assertThat(result.contentLength()).isEqualTo(bytes.length);
    }

    private static final class CapturingStorage implements DocumentStorage {
        private StorageKey key;

        @Override
        public StoredObjectMetadata put(StorageKey key, InputStream content, long length,
                                        String contentType, String sha256) throws IOException {
            this.key = key;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            content.transferTo(bytes);
            return new StoredObjectMetadata(key, bytes.size(), contentType, sha256, Instant.now(), null);
        }

        @Override public Optional<StoredObject> get(StorageKey key) { return Optional.empty(); }
        @Override public Optional<StoredObjectMetadata> head(StorageKey key) { return Optional.empty(); }
        @Override public boolean delete(StorageKey key) { return false; }
    }
}
