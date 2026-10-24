package tech.kayys.numou.warehouse.application;

import java.time.Instant;
import java.util.Objects;

/** Immutable integrity metadata for one completed recovery-audit archive artifact. */
public record WarehouseRecoveryAuditArchiveManifest(
        String exportId,
        String tenantId,
        String artifactLocation,
        String sha256Hex,
        long byteSize,
        long eventCount,
        Instant createdAt,
        String createdBy) {
    public WarehouseRecoveryAuditArchiveManifest {
        exportId = required(exportId, "exportId");
        tenantId = required(tenantId, "tenantId");
        artifactLocation = required(artifactLocation, "artifactLocation");
        sha256Hex = required(sha256Hex, "sha256Hex").toLowerCase(java.util.Locale.ROOT);
        if (!sha256Hex.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sha256Hex must be a SHA-256 hex digest");
        if (byteSize < 0) throw new IllegalArgumentException("byteSize must not be negative");
        if (eventCount < 0) throw new IllegalArgumentException("eventCount must not be negative");
        Objects.requireNonNull(createdAt, "createdAt");
        createdBy = required(createdBy, "createdBy");
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
