package tech.kayys.numou.workspace.application;
import java.util.Objects; import java.util.UUID;
/** Transport-neutral presence API; tenant and actor come from trusted host identity. */
public final class WorkspacePresenceApi {
 private final WorkspaceApi.IdentityProvider identity; private final WorkspacePresenceService service;
 public WorkspacePresenceApi(WorkspaceApi.IdentityProvider identity,WorkspacePresenceService service){this.identity=Objects.requireNonNull(identity);this.service=Objects.requireNonNull(service);}
 public WorkspacePresenceService.PresenceSnapshot heartbeat(UUID page,UUID session){var a=currentActor();return call(()->service.heartbeat(a.tenantId(),a.actorId(),page,session));}
 public WorkspacePresenceService.PresenceSnapshot list(UUID page){var a=currentActor();return call(()->service.list(a.tenantId(),a.actorId(),page));}
 public void leave(UUID page,UUID session){var a=currentActor();call(()->{service.leave(a.tenantId(),a.actorId(),page,session);return null;});}
 private WorkspaceApi.AuthenticatedActor currentActor(){var a=identity.currentActor();if(a==null||a.tenantId()==null||a.actorId()==null)throw new WorkspaceApi.ApiException(401,"An authenticated tenant and actor are required");return a;}
 private static <T>T call(Operation<T> op){try{return op.run();}catch(SecurityException e){throw new WorkspaceApi.ApiException(403,"Access denied",e);}catch(IllegalArgumentException e){throw new WorkspaceApi.ApiException(400,e.getMessage(),e);}}
 @FunctionalInterface private interface Operation<T>{T run();}
}
