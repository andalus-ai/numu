package tech.kayys.numou.vehicleservice.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.vehicleservice.domain.ServiceOffering;
import tech.kayys.numou.vehicleservice.domain.ServiceOfferingId;
import tech.kayys.numou.vehicleservice.domain.ServiceOfferingStatus;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Returns candidate appointment starts using reservation-owned capacity calculations. */
public final class FindWorkshopAppointmentSlots {
    public record Query(String tenantId, UUID workshopId, ServiceOfferingId serviceOfferingId,
                        LocalDate day, int requiredCapacity, List<String> requiredResourceTags) {
        public Query {
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
            Objects.requireNonNull(workshopId, "workshopId");
            Objects.requireNonNull(serviceOfferingId, "serviceOfferingId");
            Objects.requireNonNull(day, "day");
            if (requiredCapacity <= 0) throw new IllegalArgumentException("requiredCapacity must be positive");
            requiredResourceTags = requiredResourceTags == null ? List.of() : List.copyOf(requiredResourceTags);
        }
    }

    private final ServiceOfferingCatalog catalogue;
    private final WorkshopReservationPort reservations;

    public FindWorkshopAppointmentSlots(ServiceOfferingCatalog catalogue, WorkshopReservationPort reservations) {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.reservations = Objects.requireNonNull(reservations, "reservations");
    }

    public Uni<List<Instant>> execute(Query query) {
        Objects.requireNonNull(query, "query");
        return catalogue.find(query.tenantId(), query.serviceOfferingId()).flatMap(found -> {
            ServiceOffering offering = found.orElseThrow(() ->
                    new IllegalArgumentException("Service offering not found"));
            if (!offering.tenantId().equals(query.tenantId())) {
                throw new SecurityException("Service offering does not belong to the requested tenant");
            }
            if (offering.status() != ServiceOfferingStatus.ACTIVE) {
                throw new IllegalStateException("Service offering is not available for booking");
            }
            return reservations.availableSlots(new WorkshopReservationPort.SlotRequest(
                    query.tenantId(), query.workshopId(), offering.estimatedDurationMinutes(),
                    query.requiredCapacity(), query.requiredResourceTags(), query.day()));
        });
    }
}
