package tech.kayys.numou.documents.storage.s3;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import tech.kayys.numou.documents.storage.spi.StorageKey;
import tech.kayys.numou.documents.storage.spi.StoredObjectMetadata;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "NUMOU_RUSTFS_ENDPOINT", matches = "https?://.+")
class S3CompatibleDocumentStorageRustFsIT {
    private static S3Client client;
    private static S3CompatibleDocumentStorage storage;
    private static String bucket;

    @BeforeAll
    static void setUp() {
        String endpoint = System.getenv("NUMOU_RUSTFS_ENDPOINT");
        String accessKey = System.getenv().getOrDefault("NUMOU_RUSTFS_ACCESS_KEY", "numou-ci-access");
        String secretKey = System.getenv().getOrDefault("NUMOU_RUSTFS_SECRET_KEY", "numou-ci-secret");
        bucket = System.getenv().getOrDefault("NUMOU_RUSTFS_BUCKET", "numou-document-integration");
        client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build();
        try {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException ignored) {
            // CI may reuse a service container across retries; the bucket is an isolated test fixture.
        }
        storage = new S3CompatibleDocumentStorage(client, bucket);
    }

    @AfterAll
    static void tearDown() {
        if (storage != null) storage.close();
    }

    @Test
    void roundTripsContentMetadataAndTenantScopedKeyThenDeletesObject() throws Exception {
        byte[] payload = ("rustfs-integration-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        StorageKey key = new StorageKey("integration-tenant", "uploads/" + UUID.randomUUID() + ".txt");

        StoredObjectMetadata written = storage.put(key, new ByteArrayInputStream(payload),
                payload.length, "text/plain", sha256);

        assertEquals(payload.length, written.contentLength());
        assertEquals(java.util.Optional.of("text/plain"), written.contentType());
        assertEquals(java.util.Optional.of(sha256), written.sha256());
        assertTrue(storage.head(key).isPresent());

        try (var object = storage.get(key).orElseThrow()) {
            assertArrayEquals(payload, object.content().readAllBytes());
            assertEquals(java.util.Optional.of(sha256), object.metadata().sha256());
        }

        assertTrue(storage.delete(key));
        assertTrue(storage.head(key).isEmpty());
        assertFalse(storage.delete(key));
    }
}
