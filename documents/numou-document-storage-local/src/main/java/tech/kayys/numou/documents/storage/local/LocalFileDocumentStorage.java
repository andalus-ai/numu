package tech.kayys.numou.documents.storage.local;

import tech.kayys.numou.documents.storage.spi.DocumentStorage;
import tech.kayys.numou.documents.storage.spi.StorageKey;
import tech.kayys.numou.documents.storage.spi.StoredObjectMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Local filesystem adapter. The configured root is an application-owned
 * storage directory, not a user-controlled path. Symbolic links are rejected
 * on object paths to prevent escaping the storage root.
 */
public final class LocalFileDocumentStorage implements DocumentStorage {
    private final Path root;

    public LocalFileDocumentStorage(Path root) throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("root is required");
        }
        Files.createDirectories(root);
        this.root = root.toRealPath();
        if (!Files.isDirectory(this.root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Configured storage root is not a directory");
        }
    }

    @Override
    public StoredObjectMetadata put(StorageKey key, InputStream content, long contentLength,
                                    String contentType, String expectedSha256) throws IOException {
        if (content == null) throw new IllegalArgumentException("content is required");
        if (contentLength < 0) throw new IllegalArgumentException("contentLength must be non-negative");
        Path target = resolve(key);
        Path parent = target.getParent();
        verifyNoSymlinkComponents(parent);
        Files.createDirectories(parent);
        verifyNoSymlinkComponents(parent);
        Path temp = Files.createTempFile(parent, ".numou-upload-", ".tmp");
        MessageDigest digest = sha256();
        long copied = 0;
        try {
            try (InputStream input = new DigestInputStream(content, digest)) {
                copied = Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            if (copied != contentLength) {
                throw new IOException("Uploaded content length did not match declared content length");
            }
            String actualSha = HexFormat.of().formatHex(digest.digest());
            if (expectedSha256 != null && !expectedSha256.isBlank()
                    && !actualSha.equalsIgnoreCase(expectedSha256.trim())) {
                throw new IOException("Uploaded content checksum did not match expected SHA-256");
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return metadata(key, target, contentLength, contentType, actualSha);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public Optional<StoredObject> get(StorageKey key) throws IOException {
        Path path = resolve(key);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        verifyNoSymlinkComponents(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        StoredObjectMetadata metadata = metadata(key, path, Files.size(path), null, digest(path));
        InputStream input = Files.newInputStream(path, StandardOpenOption.READ);
        return Optional.of(new StoredObject() {
            @Override public StoredObjectMetadata metadata() { return metadata; }
            @Override public InputStream content() { return input; }
            @Override public void close() throws IOException { input.close(); }
        });
    }

    @Override
    public Optional<StoredObjectMetadata> head(StorageKey key) throws IOException {
        Path path = resolve(key);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        verifyNoSymlinkComponents(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        return Optional.of(metadata(key, path, Files.size(path), null, digest(path)));
    }

    @Override
    public boolean delete(StorageKey key) throws IOException {
        Path path = resolve(key);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return false;
        verifyNoSymlinkComponents(path);
        return Files.deleteIfExists(path);
    }

    private Path resolve(StorageKey key) throws IOException {
        if (key == null) throw new IllegalArgumentException("key is required");
        Path path = root.resolve(key.tenantId()).resolve(key.objectId()).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IOException("Storage key resolves outside the configured root");
        }
        return path;
    }

    private void verifyNoSymlinkComponents(Path path) throws IOException {
        Path current = root;
        Path relative = root.relativize(path.toAbsolutePath().normalize());
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic links are not allowed in document storage paths");
            }
        }
    }

    private StoredObjectMetadata metadata(StorageKey key, Path path, long length,
                                          String contentType, String sha) throws IOException {
        return new StoredObjectMetadata(key, length, contentType, sha,
                Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant(), null);
    }

    private String digest(Path path) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM does not provide SHA-256", e);
        }
    }
}
