package tech.kayys.numou.warehouse.application;

import java.io.IOException;
import java.io.InputStream;

/** Durable artifact boundary with idempotent chunk writes and offset fencing. */
public interface WarehouseRecoveryAuditArtifactStore {
    ArtifactSnapshot appendChunk(String tenantId, String exportId, String chunkId,
                                 long expectedOffset, byte[] bytes) throws IOException;
    ArtifactSnapshot inspect(String tenantId, String exportId) throws IOException;
    InputStream open(String tenantId, String exportId) throws IOException;

    record ArtifactSnapshot(String location, long byteSize, String sha256Hex) {
        public ArtifactSnapshot {
            if (location == null || location.isBlank()) throw new IllegalArgumentException("location is required");
            if (byteSize < 0) throw new IllegalArgumentException("byteSize must not be negative");
            if (sha256Hex == null || !sha256Hex.matches("[0-9a-fA-F]{64}"))
                throw new IllegalArgumentException("sha256Hex must be a SHA-256 hex digest");
        }
    }
}
