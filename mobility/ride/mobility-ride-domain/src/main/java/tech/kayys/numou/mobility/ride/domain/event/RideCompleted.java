package tech.kayys.numou.mobility.ride.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record RideCompleted(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        UUID riderId,
        UUID driverId,
        String vehicleType,
        String paymentMethod,
        String currency,
        BigDecimal fareSubtotal,
        BigDecimal discount,
        BigDecimal bookingFee,
        BigDecimal total,
        double distanceMeters,
        long durationSeconds,
        String basis) implements DomainEvent {

    public static RideCompleted at(Instant now, UUID rideId, UUID riderId, UUID driverId, String vehicleType, String paymentMethod, String currency, BigDecimal fareSubtotal, BigDecimal discount, BigDecimal bookingFee, BigDecimal total, double distanceMeters, long durationSeconds, String basis) {
        return new RideCompleted(UUID.randomUUID(), now, rideId, riderId, driverId, vehicleType, paymentMethod, currency, fareSubtotal, discount, bookingFee, total, distanceMeters, durationSeconds, basis);
    }

    @Override
    public String eventType() {
        return "mobility.ride.completed";
    }
}
