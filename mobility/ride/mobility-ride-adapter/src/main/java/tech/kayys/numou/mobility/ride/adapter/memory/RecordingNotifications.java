package tech.kayys.numou.mobility.ride.adapter.memory;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.mobility.ride.spi.RideNotifications;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records pushes instead of sending them (tests / dev). */
public class RecordingNotifications implements RideNotifications {

    public record Sent(String kind, UUID recipient, UUID rideId, String detail) { }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Override
    public Uni<Void> offerToDriver(UUID tenantId, UUID driverId, OfferPayload o) {
        return log("offer", driverId, o.rideId(), o.vehicleType());
    }

    @Override
    public Uni<Void> offerWithdrawn(UUID tenantId, UUID driverId, UUID rideId) {
        return log("offer-withdrawn", driverId, rideId, "");
    }

    @Override
    public Uni<Void> rideUpdateToRider(UUID tenantId, UUID riderId, UUID rideId, String status) {
        return log("rider", riderId, rideId, status);
    }

    @Override
    public Uni<Void> rideUpdateToDriver(UUID tenantId, UUID driverId, UUID rideId, String status) {
        return log("driver", driverId, rideId, status);
    }

    private Uni<Void> log(String kind, UUID to, UUID ride, String detail) {
        sent.add(new Sent(kind, to, ride, detail));
        return Uni.createFrom().voidItem();
    }

    public List<Sent> all() { return List.copyOf(sent); }

    public List<Sent> to(UUID recipient) { return sent.stream().filter(s -> s.recipient().equals(recipient)).toList(); }
}
