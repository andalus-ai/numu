package tech.kayys.syirkah.document.adapter.outbound.storage;

import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import tech.kayys.syirkah.document.application.port.DocumentStoragePort;

import java.io.IOException;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
@IfBuildProperty(name = "syirkah.document.storage", stringValue = "s3")
public class S3DocumentStorage implements DocumentStoragePort {
    private static final long MAXIMUM_S3_PUT_SIZE = 5_000_000_000L;

    private final S3Client client;
    private final S3Presigner presigner;
    private final AwsCredentialsProvider credentialsProvider;
    private final String bucket;
    private final long maximumUploadSize;

    @Inject
    public S3DocumentStorage(
            @ConfigProperty(name = "syirkah.document.s3.bucket") String bucket,
            @ConfigProperty(name = "syirkah.document.s3.region", defaultValue = "us-east-1") String region,
            @ConfigProperty(name = "syirkah.document.s3.endpoint", defaultValue = "") String endpoint,
            @ConfigProperty(name = "syirkah.document.s3.path-style", defaultValue = "false") boolean pathStyle,
            @ConfigProperty(name = "syirkah.document.max-upload-bytes",
                    defaultValue = "104857600") long maximumUploadSize
    ) {
        this.bucket = requireText(bucket, "S3 bucket");
        var awsRegion = Region.of(requireText(region, "S3 region"));
        validateMaximumUploadSize(maximumUploadSize);
        this.maximumUploadSize = maximumUploadSize;
        var serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyle)
                .build();
        this.credentialsProvider = DefaultCredentialsProvider.create();
        var clientBuilder = S3Client.builder()
                .region(awsRegion)
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(serviceConfiguration)
                .httpClientBuilder(UrlConnectionHttpClient.builder());
        var presignerBuilder = S3Presigner.builder()
                .region(awsRegion)
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(serviceConfiguration);
        if (endpoint != null && !endpoint.isBlank()) {
            var endpointUri = URI.create(endpoint);
            if (!"http".equalsIgnoreCase(endpointUri.getScheme())
                    && !"https".equalsIgnoreCase(endpointUri.getScheme())) {
                throw new IllegalArgumentException("S3 endpoint must use HTTP or HTTPS");
            }
            clientBuilder.endpointOverride(endpointUri);
            presignerBuilder.endpointOverride(endpointUri);
        }
        this.client = clientBuilder.build();
        this.presigner = presignerBuilder.build();
    }

    S3DocumentStorage(String bucket, long maximumUploadSize, S3Client client, S3Presigner presigner) {
        this.bucket = requireText(bucket, "S3 bucket");
        validateMaximumUploadSize(maximumUploadSize);
        this.maximumUploadSize = maximumUploadSize;
        this.client = Objects.requireNonNull(client);
        this.presigner = Objects.requireNonNull(presigner);
        this.credentialsProvider = null;
    }

    @PreDestroy
    void closeClients() {
        presigner.close();
        client.close();
        if (credentialsProvider instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception failure) {
                throw new IllegalStateException("Could not close the S3 credentials provider", failure);
            }
        }
    }

    @Override
    public Uni<UploadTarget> createUploadTarget(UploadTargetRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        return Uni.createFrom().item(() -> createTarget(request))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<StoredDocument> inspect(String storageKey) {
        return Uni.createFrom().item(() -> inspectBlocking(storageKey))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Void> delete(String storageKey) {
        return Uni.createFrom().item(() -> {
                    client.deleteObject(DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(validateKey(storageKey))
                            .build());
                    return Boolean.TRUE;
                })
                .replaceWithVoid()
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private UploadTarget createTarget(UploadTargetRequest request) {
        if (request.expectedSize() < 0 || request.expectedSize() > maximumUploadSize) {
            throw new IllegalArgumentException("Requested upload size is outside the configured limit");
        }
        if (request.contentType() == null || request.contentType().isBlank()) {
            throw new IllegalArgumentException("Content type is required");
        }
        var key = validateKey(request.tenantId() + "/" + request.uploadSessionId());
        var expiresAt = Objects.requireNonNull(request.expiresAt(), "expiresAt cannot be null");
        var duration = Duration.between(Instant.now(), expiresAt);
        if (duration.isNegative() || duration.isZero() || duration.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("Upload target expiry must be within the next seven days");
        }
        var putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(request.contentType())
                .contentLength(request.expectedSize())
                .ifNoneMatch("*")
                .build();
        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(duration)
                .putObjectRequest(putRequest)
                .build());
        return new UploadTarget(
                key,
                presigned.url().toString(),
                expiresAt,
                Map.of("Content-Type", request.contentType(), "If-None-Match", "*")
        );
    }

    private StoredDocument inspectBlocking(String storageKey) {
        var key = validateKey(storageKey);
        var head = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
        var size = Objects.requireNonNull(head.contentLength(), "S3 object is missing content length");
        if (size < 0 || size > maximumUploadSize) {
            throw new IllegalStateException("Stored object size is outside the configured limit");
        }
        if (head.contentType() == null || head.contentType().isBlank()) {
            throw new IllegalStateException("Stored object is missing its content type");
        }
        var digest = sha256();
        long actualSize = 0;
        try (ResponseInputStream<?> input = client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build());
             var hashed = new DigestInputStream(input, digest)) {
            var buffer = new byte[8192];
            int read;
            while ((read = hashed.read(buffer)) != -1) {
                actualSize = Math.addExact(actualSize, read);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Could not verify the S3 object contents", failure);
        }
        if (actualSize != size) {
            throw new IllegalStateException("S3 object size changed while it was being inspected");
        }
        return new StoredDocument(actualSize, head.contentType(), HexFormat.of().formatHex(digest.digest()));
    }

    private static String validateKey(String storageKey) {
        Objects.requireNonNull(storageKey, "storageKey cannot be null");
        var parts = storageKey.split("/", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid document storage key");
        }
        try {
            return UUID.fromString(parts[0]) + "/" + UUID.fromString(parts[1]);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Document storage key must contain tenant and session UUIDs", invalid);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }

    private static void validateMaximumUploadSize(long maximumUploadSize) {
        if (maximumUploadSize <= 0 || maximumUploadSize > MAXIMUM_S3_PUT_SIZE) {
            throw new IllegalArgumentException("Maximum upload size must be between 1 byte and 5 GB");
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
