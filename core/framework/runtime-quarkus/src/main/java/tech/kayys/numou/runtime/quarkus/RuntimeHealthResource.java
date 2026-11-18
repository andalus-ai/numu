package tech.kayys.numou.runtime.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.numou.runtime.ApplicationRuntimeBootstrap;
import tech.kayys.numou.runtime.RuntimeHealthSnapshot;

/**
 * HTTP transport for the framework-neutral runtime health snapshot.
 *
 * <p>The product composition root must provide one application-scoped
 * {@link ApplicationRuntimeBootstrap}. This resource never constructs or
 * starts application capabilities itself.</p>
 */
@Path("/health")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class RuntimeHealthResource {
    private final ApplicationRuntimeBootstrap bootstrap;

    @Inject
    public RuntimeHealthResource(ApplicationRuntimeBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @GET
    @Path("/live")
    public Response live() {
        RuntimeHealthSnapshot snapshot = bootstrap.health();
        return response(snapshot.live(), snapshot);
    }

    @GET
    @Path("/ready")
    public Response ready() {
        RuntimeHealthSnapshot snapshot = bootstrap.health();
        return response(snapshot.ready(), snapshot);
    }

    private Response response(boolean healthy, RuntimeHealthSnapshot snapshot) {
        return Response.status(healthy
                        ? Response.Status.OK
                        : Response.Status.SERVICE_UNAVAILABLE)
                .entity(snapshot)
                .build();
    }
}
