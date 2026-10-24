package tech.kayys.numou.warehouse.artifact.filesystem;

import tech.kayys.numou.warehouse.application.WarehouseRecoveryAuditArtifactStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Durable local-filesystem adapter for single-host deployments and tests.
 *
 * <p>Each export is isolated under opaque tenant/export hashes. A per-export OS file lock
 * serializes writers across processes. Chunk receipts make retries idempotent; retries can
 * also finish a partial append if the durable bytes match the exact prefix of the chunk.
 * Use an object-store adapter for multi-host deployments.</p>
 */
public final class FilesystemWarehouseRecoveryAuditArtifactStore implements WarehouseRecoveryAuditArtifactStore {
    private final Path root;

    public FilesystemWarehouseRecoveryAuditArtifactStore(Path root) throws IOException {
        Objects.requireNonNull(root, "root");
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
        if (!Files.isDirectory(this.root)) throw new IOException("Artifact root is not a directory");
    }

    @Override
    public ArtifactSnapshot appendChunk(String tenantId, String exportId, String chunkId,
                                        long expectedOffset, byte[] bytes) throws IOException {
        requireIdentifier(tenantId, "tenantId");
        requireIdentifier(exportId, "exportId");
        requireIdentifier(chunkId, "chunkId");
        Objects.requireNonNull(bytes, "bytes");
        if (expectedOffset < 0) throw new IllegalArgumentException("expectedOffset must not be negative");

        Path dir = exportDirectory(tenantId, exportId);
        Files.createDirectories(dir.resolve("chunks"));
        Path artifact = dir.resolve("audit.csv");
        Path lockPath = dir.resolve("write.lock");
        Path receipt = dir.resolve("chunks").resolve(sha256(chunkId.getBytes(StandardCharsets.UTF_8)) + ".receipt");
        try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = lockChannel.lock()) {
            if (!Files.exists(artifact)) Files.createFile(artifact);
            String digest = sha256(bytes);
            if (Files.exists(receipt)) {
                String[] parts = Files.readString(receipt, StandardCharsets.UTF_8).split("\\n", -1);
                if (parts.length < 3 || !parts[0].equals(Long.toString(expectedOffset))
                        || !parts[1].equals(Integer.toString(bytes.length)) || !parts[2].equals(digest))
                    throw new IOException("Chunk id was already committed with different content");
                verifyRange(artifact, expectedOffset, bytes);
                return snapshot(artifact);
            }

            long size = Files.size(artifact);
            if (size < expectedOffset) throw new IOException("Artifact is shorter than expected offset");
            long chunkEnd;
            try {
                chunkEnd = Math.addExact(expectedOffset, bytes.length);
            } catch (ArithmeticException overflow) {
                throw new IOException("Chunk end offset overflow", overflow);
            }
            if (size > chunkEnd) throw new IOException("Artifact has unexpected bytes beyond retried chunk");

            int alreadyPresent = Math.toIntExact(size - expectedOffset);
            if (alreadyPresent > 0) {
                byte[] prefix = new byte[alreadyPresent];
                try (FileChannel channel = FileChannel.open(artifact, StandardOpenOption.READ)) {
                    readFully(channel, ByteBuffer.wrap(prefix), expectedOffset);
                }
                for (int i = 0; i < alreadyPresent; i++) {
                    if (prefix[i] != bytes[i]) throw new IOException("Artifact content conflicts at expected offset");
                }
            }

            try (FileChannel channel = FileChannel.open(artifact, StandardOpenOption.WRITE)) {
                channel.position(size);
                ByteBuffer remaining = ByteBuffer.wrap(bytes, alreadyPresent, bytes.length - alreadyPresent);
                while (remaining.hasRemaining()) channel.write(remaining);
                channel.force(true);
            }

            String receiptText = expectedOffset + "\\n" + bytes.length + "\\n" + digest + "\\n";
            Path temp = Files.createTempFile(dir.resolve("chunks"), ".receipt-", ".tmp");
            try {
                Files.writeString(temp, receiptText, StandardCharsets.UTF_8,
                        StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                try (FileChannel receiptChannel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                    receiptChannel.force(true);
                }
                moveAtomically(temp, receipt);
            } finally {
                Files.deleteIfExists(temp);
            }
            forceDirectory(dir.resolve("chunks"));
            forceDirectory(dir);
            return snapshot(artifact);
        }
    }

    @Override
    public ArtifactSnapshot inspect(String tenantId, String exportId) throws IOException {
        return snapshot(existingArtifact(tenantId, exportId));
    }

    @Override
    public InputStream open(String tenantId, String exportId) throws IOException {
        return Files.newInputStream(existingArtifact(tenantId, exportId), StandardOpenOption.READ);
    }

    private Path exportDirectory(String tenantId, String exportId) {
        return root.resolve(sha256(tenantId.getBytes(StandardCharsets.UTF_8)))
                .resolve(sha256(exportId.getBytes(StandardCharsets.UTF_8))).normalize();
    }

    private Path existingArtifact(String tenantId, String exportId) throws IOException {
        requireIdentifier(tenantId, "tenantId");
        requireIdentifier(exportId, "exportId");
        Path path = exportDirectory(tenantId, exportId).resolve("audit.csv").normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new IOException("Audit artifact not found");
        return path;
    }

    private static ArtifactSnapshot snapshot(Path artifact) throws IOException {
        return new ArtifactSnapshot(artifact.toUri().toString(), Files.size(artifact), sha256(artifact));
    }

    private static void verifyRange(Path artifact, long offset, byte[] expected) throws IOException {
        long end;
        try {
            end = Math.addExact(offset, expected.length);
        } catch (ArithmeticException overflow) {
            throw new IOException("Chunk end offset overflow", overflow);
        }
        if (Files.size(artifact) < end) throw new IOException("Committed chunk is missing from artifact");
        byte[] actual = new byte[expected.length];
        try (FileChannel channel = FileChannel.open(artifact, StandardOpenOption.READ)) {
            readFully(channel, ByteBuffer.wrap(actual), offset);
        }
        if (!MessageDigest.isEqual(actual, expected))
            throw new IOException("Committed chunk content does not match receipt");
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position);
            if (read < 0) throw new IOException("Unexpected end of artifact");
            if (read == 0) continue;
            position += read;
        }
    }

    private static void moveAtomically(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to);
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException ignored) {
            // Some supported filesystems do not permit opening directories as channels.
        }
    }

    private static void requireIdentifier(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 512)
            throw new IllegalArgumentException(name + " is required and must be at most 512 characters");
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }
}