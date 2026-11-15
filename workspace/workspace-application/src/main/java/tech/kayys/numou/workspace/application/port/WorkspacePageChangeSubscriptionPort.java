package tech.kayys.numou.workspace.application.port;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Host-owned fanout subscription. Implementations must scope subscriptions by tenant, actor,
 * and page, bound subscriber resources, and provide cross-instance delivery when the host
 * deploys multiple application instances. No broker or transport SDK belongs in Workspace.
 */
@FunctionalInterface
public interface WorkspacePageChangeSubscriptionPort {
    AutoCloseable subscribe(UUID tenantId, UUID actorId, UUID pageId, Consumer<WorkspacePageChangeSignal> listener);
}
