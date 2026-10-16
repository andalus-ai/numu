package tech.kayys.numou.reservation.application;

import tech.kayys.numou.reservation.domain.Reservation;
import tech.kayys.numou.reservation.domain.ResourceId;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ReservationView(
        UUID id,
        UUID facilityId,
        String customerName,
        String customerPhone,
        String customerEmail,
        int partySize,
        Instant startsAt,
        long durationSeconds,
        String status,
        String source,
        String notes,
        Set<String> requirements,
        List<UUID> resourceIds,
        Instant checkedInAt
) {
    public static ReservationView of(Reservation r) {
        return new ReservationView(
                r.id().value(),
                r.facilityId().value(),
                r.customer().name(),
                r.customer().phone(),
                r.customer().email(),
                r.party().value(),
                r.startsAt(),
                r.duration().getSeconds(),
                r.status().name(),
                r.source().name(),
                r.notes(),
                r.requirements(),
                r.resourceIds().stream().map(ResourceId::value).sorted().toList(),
                r.checkedInAt()
        );
    }

    // Compatibility accessors for dining contexts
    public UUID venueId() { return facilityId; }
    public String guestName() { return customerName; }
    public String guestPhone() { return customerPhone; }
    public String guestEmail() { return customerEmail; }
    public Set<String> dietary() { return requirements; }
    public List<UUID> tableIds() { return resourceIds; }
    public Instant seatedAt() { return checkedInAt; }
}
