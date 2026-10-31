package tech.kayys.numou.workspace.adapter.rest;
import jakarta.enterprise.context.ApplicationScoped; import jakarta.enterprise.context.Dependent; import jakarta.enterprise.inject.Produces;
import tech.kayys.numou.workspace.application.WorkspaceApi; import tech.kayys.numou.workspace.application.WorkspacePresenceApi; import tech.kayys.numou.workspace.application.WorkspacePresenceService; import tech.kayys.numou.workspace.application.WorkspacePorts.AccessPolicy; import tech.kayys.numou.workspace.application.WorkspacePorts.PageRepository; import tech.kayys.numou.workspace.application.port.WorkspacePresenceRepository; import java.time.Clock;
/** CDI composition for the optional page presence capability. */
@ApplicationScoped public class WorkspacePresenceConfiguration {
 @Produces @Dependent WorkspacePresenceService workspacePresenceService(PageRepository pages,AccessPolicy access,WorkspacePresenceRepository presence){return new WorkspacePresenceService(pages,access,presence,Clock.systemUTC());}
 @Produces @Dependent WorkspacePresenceApi workspacePresenceApi(WorkspaceApi.IdentityProvider identity,WorkspacePresenceService service){return new WorkspacePresenceApi(identity,service);}
}
