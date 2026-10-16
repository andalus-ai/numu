package tech.kayys.syirkah.document.adapter.outbound.storage;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.syirkah.document.application.port.DocumentStoragePort;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@ApplicationScoped
@DefaultBean
@UnlessBuildProfile("prod")
public class LocalDocumentStorage implements DocumentStoragePort {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Path root;
    private final URI publicBaseUri;
    private final long maximumUploadSize;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    @Inject
    public LocalDocumentStorage(
            @ConfigProperty(name = "syirkah.document.local-root",
                    defaultValue = "${java.io.tmpdir}/syirkah-documents") String root,
            @ConfigProperty(name = "syirkah.document.upload-base-url",
                    defaultValue = "http://localhost:8083") String publicBaseUri,
            @ConfigProperty(name = "syirkah.document.max-upload-bytes",
                    defaultValue = "104857600") long maximumUploadSize
    ) {
        this(Path.of(root), URI.create(publicBaseUri), maximumUploadSize, Clock.systemUTC());
    }

    LocalDocumentStorage(Path root, URI publicBaseUri, long maximumUploadSize, Clock clock) {
        this.root = Objects.requireNonNull(root).toAbsolutePath().normalize();
        this.publicBaseUri = Objects.requireNonNull(publicBaseUri);
        this.maximumUploadSize = maximumUploadSize;
        this.clock = Objects.requireNonNull(clock);
        if (!"http".equalsIgnoreCase(publicBaseUri.getScheme())
                && !"https".equalsIgnoreCase(publicBaseUri.getScheme())) {
            throw new IllegalArgumentException("Upload base URL must use HTTP or HTTPS");
        }
        if (maximumUploadSize <= 0) {
            throw new IllegalArgumentException("Maximum upload size must be positive");
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
                    deleteBlocking(storageKey);
                    return Boolean.TRUE;
                })
                .replaceWithVoid()
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    public void acceptUpload(String tenantId, String uploadSessionId, String token,
                             String contentType, InputStream body) {
        var key = tenantId + "/" + uploadSessionId;
        var paths = resolve(key);
        Objects.requireNonNull(body, "body cannot be null");
        lock.lock();
        try {
            var metadata = loadMetadata(paths.metadata());
            authorizeUpload(metadata, token, contentType);
            if (Files.exists(paths.binary(), LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Upload session already contains an object");
            }
            var temporary = Files.createTempFile(paths.directory(), uploadSessionId + "-", ".part");
            var moved = false;
            try {
                var result = writeAndHash(body, temporary, number(metadata, "expectedSize"));
                var expectedSha256 = metadata.getProperty("expectedSha256", "");
                if (!expectedSha256.isEmpty() && !expectedSha256.equalsIgnoreCase(result.sha256())) {
                    throw new IllegalArgumentException("Uploaded object checksum does not match");
                }
                try {
                    Files.move(temporary, paths.binary(), StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException unsupportedAtomicMove) {
                    throw new IllegalStateException("Storage filesystem must support atomic file moves",
                            unsupportedAtomicMove);
                }
                moved = true;
                metadata.setProperty("actualSize", Long.toString(result.size()));
                metadata.setProperty("actualSha256", result.sha256());
                metadata.setProperty("completed", "true");
                writeMetadataAtomically(paths.metadata(), paths.directory(), metadata);
            } finally {
                if (!moved) {
                    Files.deleteIfExists(temporary);
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Local document storage failed", failure);
        } finally {
            lock.unlock();
        }
    }

    private UploadTarget createTarget(UploadTargetRequest request) {
        if (request.expectedSize() < 0 || request.expectedSize() > maximumUploadSize) {
            throw new IllegalArgumentException("Requested upload size is outside the configured limit");
        }
        var tenantId = parseUuid(request.tenantId(), "tenantId");
        var sessionId = parseUuid(request.uploadSessionId(), "uploadSessionId");
        var expiresAt = Objects.requireNonNull(request.expiresAt(), "expiresAt cannot be null");
        if (!clock.instant().isBefore(expiresAt)) {
            throw new IllegalArgumentException("Upload target expiry must be in the future");
        }
        if (request.fileName() == null || request.fileName().isBlank()
                || request.contentType() == null || request.contentType().isBlank()) {
            throw new IllegalArgumentException("File name and content type are required");
        }
        var key = tenantId + "/" + sessionId;
        var paths = resolve(key);
        var token = newToken();
        var metadata = new Properties();
        metadata.setProperty("tenantId", tenantId.toString());
        metadata.setProperty("sessionId", sessionId.toString());
        metadata.setProperty("contentType", request.contentType());
        metadata.setProperty("expectedSize", Long.toString(request.expectedSize()));
        metadata.setProperty("expectedSha256", normalizeChecksum(request.expectedSha256()));
        metadata.setProperty("expiresAt", expiresAt.toString());
        metadata.setProperty("tokenHash", sha256(token.getBytes(StandardCharsets.UTF_8)));
        metadata.setProperty("completed", "false");
        try {
            ensureSafeDirectory(paths.directory());
            writeMetadataAtomically(paths.metadata(), paths.directory(), metadata, true);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not create local upload target", failure);
        }
        var uploadUrl = publicBaseUri.resolve(
                "/api/v1/document-storage/uploads/" + tenantId + "/" + sessionId
        ).toString();
        return new UploadTarget(key, uploadUrl, expiresAt, java.util.Map.of(
                "Content-Type", request.contentType(),
                "X-Upload-Token", token
        ));
    }

    private StoredDocument inspectBlocking(String storageKey) {
        var paths = resolve(storageKey);
        try {
            var metadata = loadMetadata(paths.metadata());
            if (!Boolean.parseBoolean(metadata.getProperty("completed"))
                    || !Files.isRegularFile(paths.binary(), LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Uploaded object is not complete");
            }
            var actual = hashFile(paths.binary());
            var expectedSize = number(metadata, "expectedSize");
            if (actual.size() != expectedSize
                    || !actual.sha256().equals(metadata.getProperty("actualSha256"))) {
                throw new IllegalStateException("Stored object failed integrity verification");
            }
            var expectedSha256 = metadata.getProperty("expectedSha256", "");
            if (!expectedSha256.isEmpty() && !expectedSha256.equals(actual.sha256())) {
                throw new IllegalStateException("Stored object checksum does not match the upload request");
            }
            return new StoredDocument(actual.size(), metadata.getProperty("contentType"), actual.sha256());
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not inspect local document object", failure);
        }
    }

    private void deleteBlocking(String storageKey) {
        var paths = resolve(storageKey);
        try {
            ensureSafeDirectory(paths.directory());
            Files.deleteIfExists(paths.binary());
            Files.deleteIfExists(paths.metadata());
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not delete local document object", failure);
        }
    }

    private void authorizeUpload(Properties metadata, String token, String contentType) {
        if (token == null || contentType == null
                || !metadata.getProperty("contentType").equalsIgnoreCase(contentType)
                || !MessageDigest.isEqual(
                        metadata.getProperty("tokenHash").getBytes(StandardCharsets.US_ASCII),
                        sha256(token.getBytes(StandardCharsets.UTF_8)).getBytes(StandardCharsets.US_ASCII))) {
            throw new SecurityException("Upload authorization failed");
        }
        if (!clock.instant().isBefore(Instant.parse(metadata.getProperty("expiresAt")))) {
            throw new IllegalStateException("Upload target has expired");
        }
        if (Boolean.parseBoolean(metadata.getProperty("completed"))) {
            throw new IllegalStateException("Upload target has already been used");
        }
    }

    private static HashResult writeAndHash(InputStream body, Path target, long maximumSize) throws IOException {
        var digest = digest();
        long total = 0;
        try (var input = new DigestInputStream(body, digest);
             var output = Files.newOutputStream(target, StandardOpenOption.WRITE)) {
            var buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total = Math.addExact(total, read);
                if (total > maximumSize) {
                    throw new IllegalArgumentException("Uploaded object exceeds the requested size");
                }
                output.write(buffer, 0, read);
            }
        }
        if (total != maximumSize) {
            throw new IllegalArgumentException("Uploaded object size does not match the requested size");
        }
        return new HashResult(total, HexFormat.of().formatHex(digest.digest()));
    }

    private static HashResult hashFile(Path file) throws IOException {
        var digest = digest();
        long total = 0;
        try (var input = new DigestInputStream(Files.newInputStream(file), digest)) {
            var buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total = Math.addExact(total, read);
            }
        }
        return new HashResult(total, HexFormat.of().formatHex(digest.digest()));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private static String sha256(byte[] content) {
        return HexFormat.of().formatHex(digest().digest(content));
    }

    private static String normalizeChecksum(String checksum) {
        if (checksum == null || checksum.isBlank()) {
            return "";
        }
        if (!checksum.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException("expectedSha256 must be a 64-character hexadecimal digest");
        }
        return checksum.toLowerCase(java.util.Locale.ROOT);
    }

    private static String newToken() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static long number(Properties metadata, String name) {
        try {
            return Long.parseLong(metadata.getProperty(name));
        } catch (NumberFormatException invalidNumber) {
            throw new IllegalStateException("Upload metadata is invalid: " + name, invalidNumber);
        }
    }

    private static UUID parseUuid(String value, String name) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(name + " must be a UUID", invalid);
        }
    }

    private Paths resolve(String storageKey) {
        Objects.requireNonNull(storageKey, "storageKey cannot be null");
        var parts = storageKey.split("/", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid document storage key");
        }
        var tenantId = parseUuid(parts[0], "tenantId");
        var sessionId = parseUuid(parts[1], "uploadSessionId");
        var directory = root.resolve(tenantId.toString()).normalize();
        var binary = directory.resolve(sessionId + ".blob").normalize();
        var metadata = directory.resolve(sessionId + ".properties").normalize();
        if (!directory.startsWith(root) || !binary.startsWith(root) || !metadata.startsWith(root)) {
            throw new IllegalArgumentException("Document storage key escapes the configured root");
        }
        return new Paths(directory, binary, metadata);
    }

    private void ensureSafeDirectory(Path directory) throws IOException {
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) {
            throw new IllegalStateException("Storage root must not be a symbolic link");
        }
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory) || !directory.normalize().startsWith(root)) {
            throw new IllegalStateException("Storage directory is not safe");
        }
    }

    private static Properties loadMetadata(Path metadataFile) throws IOException {
        if (Files.isSymbolicLink(metadataFile) || !Files.isRegularFile(metadataFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Upload target does not exist");
        }
        var properties = new Properties();
        try (var input = Files.newInputStream(metadataFile)) {
            properties.load(input);
        }
        return properties;
    }

    private static void writeMetadataAtomically(Path target, Path directory, Properties properties)
            throws IOException {
        writeMetadataAtomically(target, directory, properties, false);
    }

    private static void writeMetadataAtomically(
            Path target,
            Path directory,
            Properties properties,
            boolean createOnly
    ) throws IOException {
        if (createOnly && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Upload session already has a storage target");
        }
        var temporary = Files.createTempFile(directory, "metadata-", ".part");
        var moved = false;
        try {
            try (var output = Files.newOutputStream(temporary, StandardOpenOption.WRITE)) {
                properties.store(output, null);
            }
            if (createOnly) {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } else {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } catch (AtomicMoveNotSupportedException unsupportedAtomicMove) {
            throw new IllegalStateException("Storage filesystem must support atomic file moves", unsupportedAtomicMove);
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private record Paths(Path directory, Path binary, Path metadata) {}
    private record HashResult(long size, String sha256) {}
}
