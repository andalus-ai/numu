package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Versioned dispatch route snapshot; optimization output is reviewable and reproducible. */
public record CourierRoutePlan(UUID id, TenantId tenantId, String branchCode, String courierReference,
                               LocalDate serviceDate, int version, Status status, Instant plannedAt,
                               List<Stop> stops, Long plannedDistanceMeters, Integer plannedDurationSeconds) {
    public enum Status { DRAFT, OPTIMIZED, DISPATCHED, COMPLETED, CANCELLED }
    public record Stop(int sequence, String stopReference, StopType type, Instant plannedArrivalAt) {
        public enum StopType { PICKUP, DELIVERY, HUB_TRANSFER, BREAK, RETURN_TO_DEPOT }
        public Stop {
            if (sequence < 1) throw new IllegalArgumentException("sequence must start at 1");
            stopReference = required(stopReference, "stopReference");
            Objects.requireNonNull(type); Objects.requireNonNull(plannedArrivalAt);
        }
    }
    public CourierRoutePlan {
        Objects.requireNonNull(id); Objects.requireNonNull(tenantId);
        branchCode = required(branchCode, "branchCode"); courierReference = required(courierReference, "courierReference");
        Objects.requireNonNull(serviceDate); Objects.requireNonNull(status); Objects.requireNonNull(plannedAt);
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        stops = List.copyOf(Objects.requireNonNull(stops));
        if (stops.isEmpty()) throw new IllegalArgumentException("route plan must contain stops");
        if (plannedDistanceMeters != null && plannedDistanceMeters < 0) throw new IllegalArgumentException("distance must be non-negative");
        if (plannedDurationSeconds != null && plannedDurationSeconds < 0) throw new IllegalArgumentException("duration must be non-negative");
        Set<String> seen = new HashSet<>();
        Instant previous = null;
        for (int i=0; i<stops.size(); i++) {
            Stop stop = stops.get(i);
            if (stop.sequence() != i+1) throw new IllegalArgumentException("stop sequence must be contiguous and ordered");
            if (!seen.add(stop.stopReference())) throw new IllegalArgumentException("duplicate stop reference");
            if (previous != null && stop.plannedArrivalAt().isBefore(previous))
                throw new IllegalArgumentException("planned arrival times must be non-decreasing");
            previous = stop.plannedArrivalAt();
        }
    }
    private static String required(String v, String field) {
        Objects.requireNonNull(v, field); String s=v.trim();
        if (s.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return s;
    }
}