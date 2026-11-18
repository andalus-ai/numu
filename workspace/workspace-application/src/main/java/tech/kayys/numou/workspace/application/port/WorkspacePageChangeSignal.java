package tech.kayys.numou.workspace.application.port;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Content-free invalidation signal; consumers fetch the authoritative revision via M35. */
public record WorkspacePageChangeSignal(UUID tenantId, UUID workspaceId, UUID spaceId, UUID pageId,
        long revision, UUID actorId, ChangeType changeType, Instant occurredAt) {
    public WorkspacePageChangeSignal {
        Objects.requireNonNull(tenantId, "tenantId"); Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(spaceId, "spaceId"); Objects.requireNonNull(pageId, "pageId");
        Objects.requireNonNull(actorId, "actorId"); Objects.requireNonNull(changeType, "changeType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
    }
    public enum ChangeType { CREATED, UPDATED, RESTORED }
}
