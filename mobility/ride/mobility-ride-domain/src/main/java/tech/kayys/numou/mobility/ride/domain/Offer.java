package tech.kayys.numou.mobility.ride.domain;

import java.time.Instant;
import java.util.UUID;

/** One offer of this ride to one driver. Offers are sequential: at most one is live at a time. */
public record Offer(UUID driverId, Instant offeredAt, Instant expiresAt, Status status, double distanceMeters, long etaSeconds) {

    public enum Status { OFFERED, ACCEPTED, DECLINED, EXPIRED }

    Offer with(Status next) {
        return new Offer(driverId, offeredAt, expiresAt, next, distanceMeters, etaSeconds);
    }
}
