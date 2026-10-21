package tech.kayys.numou.mobility.api;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import tech.kayys.numou.aggregate.domain.NotFoundException;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.mobility.ride.application.PublicTrackingView;
import tech.kayys.numou.mobility.ride.application.RideService;

/**
 * The share-link view: no login, rate-limited per IP. Every failure (forged, expired, unknown) answers the same 404, so a link cannot be probed.
 * NOT COMPILED in the authoring sandbox.
 */
@Path("/api/v1/public")
@Produces(MediaType.APPLICATION_JSON)
public class PublicResource {

    @Inject RideService rides;

    @GET
    @Path("/track/{token}")
    public Uni<PublicTrackingView> track(@PathParam("token") String token) {
        try {
            return rides.publicTracking(token)
                    .onFailure(t -> t instanceof BusinessRuleViolation || t instanceof NotFoundException).transform(t -> new NotFoundException("Link", "-"));
        } catch (BusinessRuleViolation e) {
            return Uni.createFrom().failure(new NotFoundException("Link", "-"));
        }
    }
}
