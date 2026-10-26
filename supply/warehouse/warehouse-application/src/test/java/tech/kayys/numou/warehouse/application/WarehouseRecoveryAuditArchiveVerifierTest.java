package tech.kayys.numou.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class WarehouseRecoveryAuditArchiveVerifierTest {
    private final WarehouseRecoveryAuditArchiveVerifier verifier = new WarehouseRecoveryAuditArchiveVerifier();

    @Test
    void verifiesMatchingBytesAndChecksum() throws Exception {
        byte[] bytes = "tenant_id,command_key\ntenant-a,cmd-1\n".getBytes(StandardCharsets.UTF_8);
        var result = verifier.verify(manifest(bytes, bytes.length), new ByteArrayInputStream(bytes));
        assertTrue(result.verified());
    }

    @Test
    void detectsTamperingAndIncorrectSize() throws Exception {
        byte[] original = "trusted archive".getBytes(StandardCharsets.UTF_8);
        byte[] altered = "tampered archive".getBytes(StandardCharsets.UTF_8);
        var result = verifier.verify(manifest(original, original.length), new ByteArrayInputStream(altered));
        assertFalse(result.verified());
        assertFalse(result.checksumMatches());
        assertFalse(result.byteSizeMatches());
    }

    @Test
    void rejectsInvalidManifestDigestAndCounts() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new WarehouseRecoveryAuditArchiveManifest(
                "export-1", "tenant-a", "s3://bucket/key", "not-a-digest", 0, 0,
                Instant.parse("2026-01-01T00:00:00Z"), "operator-1"));
        var valid = manifest(new byte[0], 0);
        assertTrue(verifier.verify(valid, new ByteArrayInputStream(new byte[0])).verified());
    }

    @Test
    void persistedManifestEvidenceIsStableAcrossVerificationRetries() throws Exception {
        byte[] bytes = "durable archive".getBytes(StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        var original = new WarehouseRecoveryAuditArchiveManifest("export-1", "tenant-a",
                "archive://tenant-a/export-1", hash, bytes.length, 7,
                Instant.parse("2026-01-01T00:00:00Z"), "operator-1");
        var retryCandidate = new WarehouseRecoveryAuditArchiveManifest("export-1", "tenant-a",
                "archive://tenant-a/export-1", hash, bytes.length, 7,
                Instant.parse("2026-01-02T00:00:00Z"), "operator-1");

        assertTrue(original.sameExportEvidence(retryCandidate));
        assertTrue(verifier.verify(original, new ByteArrayInputStream(bytes)).verified());
    }

    @Test
    void persistedManifestRejectsCheckpointOrArtifactEvidenceDrift() throws Exception {
        byte[] bytes = "durable archive".getBytes(StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        var original = new WarehouseRecoveryAuditArchiveManifest("export-1", "tenant-a",
                "archive://tenant-a/export-1", hash, bytes.length, 7,
                Instant.parse("2026-01-01T00:00:00Z"), "operator-1");
        var changed = new WarehouseRecoveryAuditArchiveManifest("export-1", "tenant-a",
                "archive://tenant-a/export-1", hash, bytes.length, 8,
                Instant.parse("2026-01-01T00:00:00Z"), "operator-1");

        assertFalse(original.sameExportEvidence(changed));
    }

    private static WarehouseRecoveryAuditArchiveManifest manifest(byte[] bytes, long declaredSize) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        return new WarehouseRecoveryAuditArchiveManifest("export-1", "tenant-a", "archive://tenant-a/export-1",
                hash, declaredSize, 1, Instant.parse("2026-01-01T00:00:00Z"), "operator-1");
    }
}
