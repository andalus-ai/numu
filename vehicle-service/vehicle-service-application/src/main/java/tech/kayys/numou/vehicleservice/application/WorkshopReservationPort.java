package tech.kayys.numou.vehicleservice.application;

import io.smallrye.mutiny.Uni;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Application boundary to Numou's generic reservation capability.
 *
 * Implementations must use the reservation engine's atomic capacity allocation, tenant-scoped
 * persistence and idempotency support. They must honor requested duration and required resource
 * constraints; a read-only availability check is advisory and is never a substitute for atomic
 * reservation when committing a booking.
 */
public interface WorkshopReservationPort {
    record SlotRequest(
            String tenantId,
            UUID workshopId,
            int durationMinutes,
            int requiredCapacity,
            List<String> requiredResourceTags,
            LocalDate day
    ) {
        public SlotRequest {
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
            if (workshopId == null) throw new IllegalArgumentException("workshopId is required");
            if (durationMinutes <= 0) throw new IllegalArgumentException("durationMinutes must be positive");
            if (requiredCapacity <= 0) throw new IllegalArgumentException("requiredCapacity must be positive");
            requiredResourceTags = requiredResourceTags == null ? List.of() : List.copyOf(requiredResourceTags);
            if (day == null) throw new IllegalArgumentException("day is required");
        }
    }

    record ReserveCommand(
            String tenantId,
            UUID workshopId,
            UUID customerId,
            UUID vehicleId,
            UUID serviceOfferingId,
            Instant startsAt,
            int durationMinutes,
            int requiredCapacity,
            List<String> requiredResourceTags,
            String idempotencyKey
    ) {
        public ReserveCommand {
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
            if (workshopId == null || customerId == null || vehicleId == null || serviceOfferingId == null) {
                throw new IllegalArgumentException("workshop, customer, vehicle and service offering IDs are required");
            }
            if (startsAt == null) throw new IllegalArgumentException("startsAt is required");
            if (durationMinutes <= 0) throw new IllegalArgumentException("durationMinutes must be positive");
            if (requiredCapacity <= 0) throw new IllegalArgumentException("requiredCapacity must be positive");
            requiredResourceTags = requiredResourceTags == null ? List.of() : List.copyOf(requiredResourceTags);
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("idempotencyKey is required");
            }
        }
    }

    Uni<List<Instant>> availableSlots(SlotRequest request);

    /** Returns the same reservation ID when the same tenant-scoped idempotency key is retried. */
    Uni<UUID> reserve(ReserveCommand command);

    Uni<Void> cancel(String tenantId, UUID reservationId, String reason);

    Uni<Void> reschedule(String tenantId, UUID reservationId, Instant newStart, int durationMinutes);
}
