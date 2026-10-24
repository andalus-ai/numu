package tech.kayys.numou.workspace.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Tenant-owned collection of structured tables, independent from any business-domain aggregate. */
public record WorkspaceDatabase(
        UUID id,
        UUID tenantId,
        UUID workspaceId,
        String name,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public WorkspaceDatabase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(createdBy, "createdBy");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("database name cannot be blank");
        name = name.trim();
        if (name.length() > 200) throw new IllegalArgumentException("database name cannot exceed 200 characters");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt cannot precede createdAt");
    }

    public static WorkspaceDatabase create(UUID tenantId, UUID workspaceId, UUID actorId, String name, Instant now) {
        return new WorkspaceDatabase(UUID.randomUUID(), tenantId, workspaceId, name, actorId, now, now);
    }
}
