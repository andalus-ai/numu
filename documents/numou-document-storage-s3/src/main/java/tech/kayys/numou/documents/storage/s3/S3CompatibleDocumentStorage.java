package tech.kayys.numou.documents.storage.s3;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import tech.kayys.numou.documents.storage.spi.DocumentStorage;
import tech.kayys.numou.documents.storage.spi.StorageKey;
import tech.kayys.numou.documents.storage.spi.StoredObjectMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * S3 API adapter suitable for AWS S3 and S3-compatible services such as
 * RustFS. Construct the client in the composition layer with endpoint,
 * region, credentials provider, and path-style options for the deployment.
 */
public final class S3CompatibleDocumentStorage implements DocumentStorage {
    private static final String SHA256_METADATA = "numou-sha256";
    private final S3Client client;
    private final String bucket;

    public S3CompatibleDocumentStorage(S3Client client, String bucket) {
        if (client == null) throw new IllegalArgumentException("client is required");
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("bucket is required");
        this.client = client;
        this.bucket = bucket.trim();
    }

    @Override
    public StoredObjectMetadata put(StorageKey key, InputStream content, long contentLength,
                                    String contentType, String sha256) throws IOException {
        if (content == null) throw new IllegalArgumentException("content is required");
        if (contentLength < 0) throw new IllegalArgumentException("contentLength must be non-negative");
        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(physicalKey(key))
                .contentLength(contentLength);
        if (contentType != null && !contentType.isBlank()) request.contentType(contentType.trim());
        if (sha256 != null && !sha256.isBlank()) request.metadata(Map.of(SHA256_METADATA, sha256.trim()));
        client.putObject(request.build(), RequestBody.fromInputStream(content, contentLength));
        return head(key).orElseThrow(() -> new IOException("Object was uploaded but could not be read back"));
    }

    @Override
    public Optional<StoredObject> get(StorageKey key) throws IOException {
        try {
            ResponseInputStream<GetObjectResponse> stream = client.getObject(GetObjectRequest.builder()
                    .bucket(bucket).key(physicalKey(key)).build());
            GetObjectResponse response = stream.response();
            StoredObjectMetadata metadata = metadata(key, response.contentLength(), response.contentType(),
                    response.metadata(), response.lastModified(), response.versionId());
            return Optional.of(new StoredObject() {
                @Override public StoredObjectMetadata metadata() { return metadata; }
                @Override public InputStream content() { return stream; }
                @Override public void close() throws IOException { stream.close(); }
            });
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return Optional.empty();
            throw new IOException("S3-compatible storage read failed", e);
        }
    }

    @Override
    public Optional<StoredObjectMetadata> head(StorageKey key) throws IOException {
        try {
            HeadObjectResponse response = client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket).key(physicalKey(key)).build());
            return Optional.of(metadata(key, response.contentLength(), response.contentType(),
                    response.metadata(), response.lastModified(), response.versionId()));
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return Optional.empty();
            throw new IOException("S3-compatible storage metadata lookup failed", e);
        }
    }

    @Override
    public boolean delete(StorageKey key) throws IOException {
        boolean existed = head(key).isPresent();
        if (!existed) return false;
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket).key(physicalKey(key)).build());
            return true;
        } catch (S3Exception e) {
            throw new IOException("S3-compatible storage delete failed", e);
        }
    }

    @Override
    public void close() {
        client.close();
    }

    private String physicalKey(StorageKey key) {
        if (key == null) throw new IllegalArgumentException("key is required");
        return key.tenantId() + "/" + key.objectId();
    }

    private StoredObjectMetadata metadata(StorageKey key, Long length, String contentType,
                                          Map<String, String> userMetadata,
                                          Instant modified, String version) {
        String sha = userMetadata == null ? null : userMetadata.get(SHA256_METADATA);
        return new StoredObjectMetadata(key, length == null ? 0 : length, contentType,
                sha, modified, version);
    }
}
