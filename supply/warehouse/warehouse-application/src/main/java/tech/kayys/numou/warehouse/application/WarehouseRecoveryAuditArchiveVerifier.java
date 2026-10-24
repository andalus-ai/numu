package tech.kayys.numou.warehouse.application;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Verifies archived artifact bytes against a previously persisted immutable manifest. */
public final class WarehouseRecoveryAuditArchiveVerifier {
    private static final int BUFFER_SIZE = 8192;

    public VerificationResult verify(WarehouseRecoveryAuditArchiveManifest manifest, InputStream artifact)
            throws IOException {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(artifact, "artifact");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
        byte[] buffer = new byte[BUFFER_SIZE];
        long size = 0;
        int read;
        while ((read = artifact.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
            size = Math.addExact(size, read);
        }
        String actualHash = HexFormat.of().formatHex(digest.digest());
        boolean sizeMatches = size == manifest.byteSize();
        boolean checksumMatches = MessageDigest.isEqual(
                HexFormat.of().parseHex(actualHash), HexFormat.of().parseHex(manifest.sha256Hex()));
        return new VerificationResult(manifest.exportId(), size, actualHash, sizeMatches, checksumMatches);
    }

    public record VerificationResult(String exportId, long actualByteSize, String actualSha256Hex,
                                     boolean byteSizeMatches, boolean checksumMatches) {
        public boolean verified() { return byteSizeMatches && checksumMatches; }
    }
}
