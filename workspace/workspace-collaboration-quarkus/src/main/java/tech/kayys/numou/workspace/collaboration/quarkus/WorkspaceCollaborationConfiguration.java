package tech.kayys.numou.workspace.collaboration.quarkus;

import io.quarkus.arc.DefaultBean;
import io.smallrye.config.ConfigMapping;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import tech.kayys.numou.workspace.collaboration.local.LocalWorkspacePageChangeHub;\nimport tech.kayys.numou.workspace.adapter.rest.WorkspaceSseHeartbeatScheduler;

/**
 * Optional CDI composition for standalone and modular-monolith hosts. A host that supplies its
 * own subscription/notifier implementation can disable this default producer or omit this module.
 */
@ApplicationScoped
public class WorkspaceCollaborationConfiguration {

    @Produces
    @ApplicationScoped
    @DefaultBean
    LocalWorkspacePageChangeHub localWorkspacePageChangeHub(CollaborationLimits limits) {
        return new LocalWorkspacePageChangeHub(
                limits.maxConnections(), limits.maxPerTenant(), limits.maxPerPage(), limits.maxPerActor());
    }

    void closeHub(@Disposes LocalWorkspacePageChangeHub hub) {
        hub.close();
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    WorkspaceSseHeartbeatScheduler heartbeatScheduler(CollaborationLimits limits) {
        return new QuarkusWorkspaceSseHeartbeatScheduler(limits.heartbeatIntervalSeconds());
    }

    void closeHeartbeatScheduler(@Disposes WorkspaceSseHeartbeatScheduler scheduler) {
        if (scheduler instanceof AutoCloseable closeable) {
            try { closeable.close(); } catch (Exception ignored) { /* shutdown is best-effort */ }
        }
    }

    @ConfigMapping(prefix = "numou.workspace.collaboration")
    public interface CollaborationLimits {
        @io.smallrye.config.WithDefault("2000") int maxConnections();
        @io.smallrye.config.WithDefault("200") int maxPerTenant();
        @io.smallrye.config.WithDefault("50") int maxPerPage();
        @io.smallrye.config.WithDefault("20") int maxPerActor();
        @io.smallrye.config.WithDefault("25") int heartbeatIntervalSeconds();
    }
}
