package tech.kayys.numou.mobility.api;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import tech.kayys.numou.mobility.ride.spi.RideNotifications;

import java.util.UUID;

/**
 * Default notifier: logs that a push <em>would</em> go out (ids only, no places or names). Replace this bean with an FCM/APNs
 * adapter. Apps also poll {@code GET /driver/offer} and the ride stream, so the system works (slower) without push.
 */
@ApplicationScoped
public class NoopRideNotifications implements RideNotifications {

    private static final Logger LOG = Logger.getLogger(NoopRideNotifications.class);

    @Override
    public Uni<Void> offerToDriver(UUID tenantId, UUID driverId, OfferPayload o) {
        LOG.debugf("push offer ride=%s driver=%s", o.rideId(), driverId);
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> offerWithdrawn(UUID tenantId, UUID driverId, UUID rideId) {
        LOG.debugf("push offer-withdrawn ride=%s driver=%s", rideId, driverId);
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> rideUpdateToRider(UUID tenantId, UUID riderId, UUID rideId, String status) {
        LOG.debugf("push rider ride=%s status=%s", rideId, status);
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> rideUpdateToDriver(UUID tenantId, UUID driverId, UUID rideId, String status) {
        LOG.debugf("push driver ride=%s status=%s", rideId, status);
        return Uni.createFrom().voidItem();
    }
}
