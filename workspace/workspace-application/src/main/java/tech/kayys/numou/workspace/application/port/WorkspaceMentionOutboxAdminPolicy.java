package tech.kayys.numou.workspace.application.port;

import java.util.UUID;

/**
 * Host-owned authorization boundary for tenant-level mention outbox operations.
 * Implementations must authorize the authenticated actor for operational access
 * within the supplied tenant; possession of a delivery ID is never authorization.
 */
@FunctionalInterface
public interface WorkspaceMentionOutboxAdminPolicy {
    void requireManage(UUID tenantId, UUID actorId);
}
