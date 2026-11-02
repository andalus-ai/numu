package tech.kayys.numou.workspace.adapter.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import tech.kayys.numou.workspace.application.WorkspaceApi;
import tech.kayys.numou.workspace.application.WorkspacePageSyncApi;
import tech.kayys.numou.workspace.application.WorkspacePageSyncService;
import tech.kayys.numou.workspace.application.WorkspacePorts.AccessPolicy;
import tech.kayys.numou.workspace.application.WorkspacePorts.PageRepository;

/** Host-composed CDI wiring for page revision synchronization. */
@ApplicationScoped
public class WorkspacePageSyncConfiguration {
    @Produces @Dependent
    WorkspacePageSyncService workspacePageSyncService(PageRepository pages, AccessPolicy access) {
        return new WorkspacePageSyncService(pages, access);
    }
    @Produces @Dependent
    WorkspacePageSyncApi workspacePageSyncApi(WorkspaceApi.IdentityProvider identity, WorkspacePageSyncService service) {
        return new WorkspacePageSyncApi(identity, service);
    }
}
