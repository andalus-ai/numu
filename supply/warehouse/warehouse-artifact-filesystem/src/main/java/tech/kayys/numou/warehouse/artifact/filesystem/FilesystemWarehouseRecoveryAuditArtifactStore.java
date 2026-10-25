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
import java.util.concurrent.locks.ReentrantLock;

/**
 * Durable local-filesystem adapter for single-host deployments and tests.
 *
 * <p>Each export is isolated under opaque tenant/export hashes. A per-export OS file lock
 * serializes writers across processes. Chunk receipts make retries idempotent; retries can
 * also finish a partial append if the durable bytes match the exact prefix of the chunk.
 * Use an object-store adapter for multi-host deployments.</p>
 */
public final class FilesystemWarehouseRecoveryAuditArtifactStore implements WarehouseRecoveryAuditArtifactStore {
    private static final ReentrantLock[] JVM_LOCKS = createLocks();
    private final Path root;

    private static ReentrantLock[] createLocks() {
        ReentrantLock[] locks = new ReentrantLock[64];
        for (int i = 0; i < locks.length; i++) locks[i] = new ReentrantLock();
        return locks;
    }

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
        ReentrantLock processLock = JVM_LOCKS[Math.floorMod(lockPath.hashCode(), JVM_LOCKS.length)];
        processLock.lock();
        try {
        try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = lockChannel.lock()) {
            if (!Files.exists(artifact)) Files.createFile(artifact);
            verifyDurableReceipts(dir, artifact);
            String digest = sha256(bytes);
            if (Files.exists(receipt)) {
                String receiptText = Files.readString(receipt, StandardCharsets.UTF_8);
                String[] parts = receiptText.split("\\n", -1);
                // Receipt format is exactly: offset, byte length, SHA-256, final newline.
                // Do not accept extra fields or truncated records as valid durable evidence.
                if (parts.length != 4 || !parts[3].isEmpty()
                        || !parts[0].equals(Long.toString(expectedOffset))
                        || !parts[1].equals(Integer.toString(bytes.length))
                        || !parts[2].matches("[0-9a-f]{64}") || !parts[2].equals(digest))
                    throw new IOException("Chunk receipt is malformed or conflicts with retry content");
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

            String receiptText = expectedOffset + "\n" + bytes.length + "\n" + digest + "\n";
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
        } finally {
            processLock.unlock();
        }
    }

    @Override
    public ArtifactSnapshot inspect(String tenantId, String exportId) throws IOException {
        Path artifact = existingArtifact(tenantId, exportId);
        verifyDurableReceipts(artifact.getParent(), artifact);
        return snapshot(artifact);
    }

    @Override
    public InputStream open(String tenantId, String exportId) throws IOException {
        Path artifact = existingArtifact(tenantId, exportId);
        verifyDurableReceipts(artifact.getParent(), artifact);
        return Files.newInputStream(artifact, StandardOpenOption.READ);
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

    /**
     * Verify every durable receipt against the artifact before exposing or extending it.
     * Missing receipts are intentionally tolerated: a crash may occur after bytes are forced
     * but before the receipt is atomically committed, and appendChunk can recover that window.
     */
    private static void verifyDurableReceipts(Path exportDirectory, Path artifact) throws IOException {
        Path receipts = exportDirectory.resolve("chunks");
        if (!Files.isDirectory(receipts)) return;
        long artifactSize = Files.size(artifact);
        java.util.List<ReceiptRange> ranges = new java.util.ArrayList<>();
        try (var files = Files.newDirectoryStream(receipts, "*.receipt")) {
            for (Path receipt : files) {
                String text = Files.readString(receipt, StandardCharsets.UTF_8);
                String[] parts = text.split("\\n", -1);
                if (parts.length != 4 || !parts[3].isEmpty()
                        || !parts[0].matches("0|[1-9][0-9]*")
                        || !parts[1].matches("0|[1-9][0-9]*")
                        || !parts[2].matches("[0-9a-f]{64}")) {
                    throw new IOException("Malformed durable chunk receipt: " + receipt.getFileName());
                }
                final long offset;
                final int length;
                try {
                    offset = Long.parseLong(parts[0]);
                    length = Integer.parseInt(parts[1]);
                } catch (NumberFormatException invalid) {
                    throw new IOException("Invalid durable chunk receipt numbers: " + receipt.getFileName(), invalid);
                }
                long end;
                try {
                    end = Math.addExact(offset, length);
                } catch (ArithmeticException overflow) {
                    throw new IOException("Durable chunk receipt offset overflow: " + receipt.getFileName(), overflow);
                }
                if (offset < 0 || length < 0 || end > artifactSize)
                    throw new IOException("Durable chunk receipt exceeds artifact bounds: " + receipt.getFileName());
                byte[] actual = new byte[length];
                try (FileChannel channel = FileChannel.open(artifact, StandardOpenOption.READ)) {
                    readFully(channel, ByteBuffer.wrap(actual), offset);
                }
                if (!MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.US_ASCII),
                        sha256(actual).getBytes(StandardCharsets.US_ASCII))) {
                    throw new IOException("Artifact bytes do not match durable chunk receipt: " + receipt.getFileName());
                }
                ranges.add(new ReceiptRange(offset, end, receipt.getFileName().toString()));
            }
        }

        // A crash can leave an unreceipted tail, but not a hole before a later committed receipt.
        ranges.sort(java.util.Comparator.comparingLong(ReceiptRange::offset)
                .thenComparingLong(ReceiptRange::end));
        long committedEnd = 0;
        for (ReceiptRange range : ranges) {
            if (range.offset() != committedEnd) {
                String reason = range.offset() < committedEnd ? "overlapping" : "non-contiguous";
                throw new IOException("Durable chunk receipts contain " + reason + " ranges at "
                        + range.receiptName());
            }
            // Empty chunks are accepted only at the current boundary.
            if (range.end() > range.offset()) committedEnd = range.end();
        }
    }

    private record ReceiptRange(long offset, long end, String receiptName) {}

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