package tech.kayys.numou.mobility.ride.spi;

import io.smallrye.mutiny.Uni;

import java.util.UUID;

/**
 * Push notifications (FCM/APNs/WebSocket). Best effort: a failure here must never fail a ride. Clients also poll, so a
 * lost push delays the UI by seconds but never loses state.
 */
public interface RideNotifications {

    record OfferPayload(UUID rideId, String vehicleType, double pickupDistanceMeters, long etaSeconds, long expiresInSeconds,
                        String pickupArea, String dropoffArea, java.math.BigDecimal fare, String currency, String paymentMethod) { }

    Uni<Void> offerToDriver(UUID tenantId, UUID driverId, OfferPayload offer);

    Uni<Void> offerWithdrawn(UUID tenantId, UUID driverId, UUID rideId);

    Uni<Void> rideUpdateToRider(UUID tenantId, UUID riderId, UUID rideId, String status);

    Uni<Void> rideUpdateToDriver(UUID tenantId, UUID driverId, UUID rideId, String status);
}
