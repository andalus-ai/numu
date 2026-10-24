package tech.kayys.numou.omnichannel.payloadstore;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.omnichannel.core.ChannelContext;
import tech.kayys.numou.omnichannel.core.InboundPayloadReference;
import tech.kayys.numou.omnichannel.core.InboundPayloadStore;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

public final class EncryptedFileSystemInboundPayloadStore implements InboundPayloadStore {
    private static final int MAGIC = 0x4E4F504C;
    private static final byte VERSION = 1;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int HEADER_LENGTH = Integer.BYTES + 1 + Long.BYTES + NONCE_LENGTH;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path root;
    private final TenantPayloadKeyProvider keyProvider;

    public EncryptedFileSystemInboundPayloadStore(Path root, TenantPayloadKeyProvider keyProvider) {
        this.root = Objects.requireNonNull(root, "root must not be null").toAbsolutePath().normalize();
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider must not be null");
        try {
            Files.createDirectories(this.root);
            restrictPermissions(this.root, true);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize encrypted payload root", exception);
        }
    }

    @Override
    public InboundPayloadReference store(ChannelContext context, byte[] payload, String expectedSha256, Instant retentionUntil) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(retentionUntil, "retentionUntil must not be null");
        if (!retentionUntil.isAfter(Instant.now())) throw new IllegalArgumentException("retentionUntil must be in the future");
        verifyDigest(payload, expectedSha256);

        String id = UUID.randomUUID().toString();
        Path directory = tenantDirectory(context.tenantId());
        Path target = directory.resolve(id + ".payload");
        Path temporary = directory.resolve("." + id + ".tmp");
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        try {
            Files.createDirectories(directory);
            restrictPermissions(directory, true);
            byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, context.tenantId(), nonce, retentionUntil.toEpochMilli(), payload);
            ByteBuffer data = ByteBuffer.allocate(HEADER_LENGTH + encrypted.length);
            data.putInt(MAGIC).put(VERSION).putLong(retentionUntil.toEpochMilli()).put(nonce).put(encrypted).flip();
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                restrictPermissions(temporary, false);
                while (data.hasRemaining()) channel.write(data);
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
            restrictPermissions(target, false);
            return new InboundPayloadReference(id);
        } catch (IOException | GeneralSecurityException exception) {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* preserve original failure */ }
            throw new IllegalStateException("Unable to persist encrypted inbound payload", exception);
        }
    }

    @Override
    public Optional<byte[]> load(ChannelContext context, InboundPayloadReference reference) {
        Objects.requireNonNull(context, "context must not be null");
        Path file = payloadPath(context.tenantId(), Objects.requireNonNull(reference, "reference must not be null"));
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            byte[] contents = Files.readAllBytes(file);
            if (contents.length < HEADER_LENGTH + 16) throw new IllegalStateException("Stored payload envelope is malformed");
            ByteBuffer data = ByteBuffer.wrap(contents);
            if (data.getInt() != MAGIC || data.get() != VERSION) throw new IllegalStateException("Unsupported payload envelope");
            long expiresAt = data.getLong();
            if (!Instant.ofEpochMilli(expiresAt).isAfter(Instant.now())) {
                Files.deleteIfExists(file);
                return Optional.empty();
            }
            byte[] nonce = new byte[NONCE_LENGTH];
            data.get(nonce);
            byte[] encrypted = new byte[data.remaining()];
            data.get(encrypted);
            return Optional.of(crypt(Cipher.DECRYPT_MODE, context.tenantId(), nonce, expiresAt, encrypted));
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to read encrypted inbound payload", exception);
        }
    }

    @Override
    public boolean delete(ChannelContext context, InboundPayloadReference reference) {
        Objects.requireNonNull(context, "context must not be null");
        try {
            return Files.deleteIfExists(payloadPath(context.tenantId(), Objects.requireNonNull(reference, "reference must not be null")));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to delete encrypted inbound payload", exception);
        }
    }

    /** Removes expired payload files, bounded by limit, and returns the number deleted. */
    public int purgeExpired(Instant now, int limit) {
        Objects.requireNonNull(now, "now must not be null");
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        int removed = 0;
        try (DirectoryStream<Path> tenants = Files.newDirectoryStream(root)) {
            for (Path tenant : tenants) {
                if (removed >= limit) break;
                if (!Files.isDirectory(tenant)) continue;
                try (DirectoryStream<Path> files = Files.newDirectoryStream(tenant, "*.payload")) {
                    for (Path file : files) {
                        if (removed >= limit) break;
                        if (isExpired(file, now) && Files.deleteIfExists(file)) removed++;
                    }
                }
            }
            return removed;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to purge expired inbound payloads", exception);
        }
    }

    private boolean isExpired(Path file, Instant now) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(Integer.BYTES + 1 + Long.BYTES);
            int read = channel.read(header);
            if (read != header.capacity()) return true;
            header.flip();
            if (header.getInt() != MAGIC || header.get() != VERSION) return true;
            return !Instant.ofEpochMilli(header.getLong()).isAfter(now);
        }
    }

    private byte[] crypt(int mode, TenantId tenantId, byte[] nonce, long expiresAt, byte[] input) throws GeneralSecurityException {
        SecretKey key = Objects.requireNonNull(keyProvider.keyFor(tenantId), "tenant key must not be null");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD((tenantId.toString() + ":" + expiresAt + ":" + VERSION).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return cipher.doFinal(input);
    }

    private Path tenantDirectory(TenantId tenantId) {
        return root.resolve(HexFormat.of().formatHex(sha256Bytes(tenantId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }

    private Path payloadPath(TenantId tenantId, InboundPayloadReference reference) {
        final String id;
        try { id = UUID.fromString(reference.value()).toString(); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("payload reference is not a valid opaque identifier"); }
        Path result = tenantDirectory(tenantId).resolve(id + ".payload").normalize();
        if (!result.startsWith(root)) throw new IllegalArgumentException("payload reference escapes storage root");
        return result;
    }

    private static void verifyDigest(byte[] payload, String expectedSha256) {
        Objects.requireNonNull(expectedSha256, "expectedSha256 must not be null");
        String expected = expectedSha256.trim().toLowerCase(Locale.ROOT);
        if (!expected.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("expectedSha256 must be a SHA-256 hex digest");
        String actual = HexFormat.of().formatHex(sha256Bytes(payload));
        if (!MessageDigest.isEqual(actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII), expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("payload digest does not match expectedSha256");
        }
    }

    private static byte[] sha256Bytes(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (GeneralSecurityException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private static void restrictPermissions(Path path, boolean directory) {
        try {
            Set<PosixFilePermission> permissions = directory
                    ? EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
                    : EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Configure equivalent ACLs on non-POSIX systems.
        }
    }
}