package tech.kayys.numou.mobility.ride.application;

import tech.kayys.numou.mobility.driver.application.DriverView;
import tech.kayys.numou.mobility.kernel.geo.LatLng;
import tech.kayys.numou.mobility.ride.domain.Offer;
import tech.kayys.numou.mobility.ride.domain.Ride;
import tech.kayys.numou.mobility.ride.domain.RideStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * What a caller is allowed to see of a ride.
 * <ul>
 *   <li>The rider sees exact places, the assigned driver (name, vehicle, plate, rating, live position) and the pickup PIN
 *       while a driver is on the way. Never the driver's phone number.</li>
 *   <li>The assigned driver sees exact places and the payment method. Never the PIN.</li>
 *   <li>A driver who has only been <em>offered</em> the ride sees coarse (~1 km) places and the fare, nothing else.</li>
 * </ul>
 */
public record RideView(UUID id, String status, String vehicle, PlaceView pickup, PlaceView dropoff, String paymentMethod,
                       BigDecimal quotedTotal, BigDecimal finalTotal, String currency, DriverInfo driver, String pin, long etaSeconds,
                       BigDecimal cancellationFee, Instant requestedAt, Instant searchDeadline, boolean sos, int ratingGiven, BigDecimal tip) {

    public record PlaceView(double lat, double lng, String label, boolean exact) { }

    public record DriverInfo(UUID id, String name, String vehicleType, String plate, String model, String color, double rating, Double lat, Double lng) { }

    static RideView forRider(Ride r, DriverView d, LatLng driverAt, String pin) {
        final boolean showPin = pin != null && (r.status() == RideStatus.DRIVER_ASSIGNED || r.status() == RideStatus.DRIVER_ARRIVED);
        return build(r, d, driverAt, showPin ? pin : null, true, true, r.riderRatingOfDriver());
    }

    static RideView forAssignedDriver(Ride r) {
        return build(r, null, null, null, true, false, r.driverRatingOfRider());
    }

    static RideView forOfferedDriver(Ride r) {
        return build(r, null, null, null, false, false, 0);
    }

    private static RideView build(Ride r, DriverView d, LatLng driverAt, String pin, boolean exact, boolean riderSide, int rating) {
        final var p = r.pickup().point();
        final var q = r.dropoff().point();
        final var pickup = exact ? new PlaceView(p.lat(), p.lng(), r.pickup().label(), true) : new PlaceView(Math.round(p.lat() * 100) / 100.0, Math.round(p.lng() * 100) / 100.0, "", false);
        final var dropoff = exact ? new PlaceView(q.lat(), q.lng(), r.dropoff().label(), true) : new PlaceView(Math.round(q.lat() * 100) / 100.0, Math.round(q.lng() * 100) / 100.0, "", false);
        final DriverInfo info = d == null ? null : new DriverInfo(d.id(), d.name(), d.vehicleType(), d.plate(), d.model(), d.color(), d.rating(),
                driverAt == null ? null : driverAt.lat(), driverAt == null ? null : driverAt.lng());
        final long eta = r.offers().stream().filter(o -> o.status() == Offer.Status.ACCEPTED || o.status() == Offer.Status.OFFERED)
                .map(Offer::etaSeconds).reduce((a, b) -> b).orElse(0L);
        return new RideView(r.id().value(), r.status().name(), r.vehicle().name(), pickup, dropoff, r.paymentMethod().name(), r.quote().total(),
                r.finalFare() == null ? null : r.finalFare().total(), r.quote().currency(), info, pin, eta, r.cancelFee(), r.requestedAt(),
                r.searchDeadline(), r.sosAt() != null, rating, riderSide ? r.tip() : null);
    }
}
