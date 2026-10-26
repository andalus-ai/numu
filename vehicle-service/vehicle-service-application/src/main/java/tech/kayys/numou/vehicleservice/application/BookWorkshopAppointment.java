package tech.kayys.numou.vehicleservice.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.vehicleservice.domain.ServiceOffering;
import tech.kayys.numou.vehicleservice.domain.ServiceOfferingId;
import tech.kayys.numou.vehicleservice.domain.ServiceOfferingStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Customer appointment use case. Booking capacity remains owned by the reservation capability. */
public final class BookWorkshopAppointment {
    public record Command(
            String tenantId,
            UUID workshopId,
            UUID customerId,
            UUID vehicleId,
            ServiceOfferingId serviceOfferingId,
            Instant startsAt,
            String idempotencyKey,
            int requiredCapacity,
            List<String> requiredResourceTags
    ) {
        public Command {
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
            Objects.requireNonNull(workshopId, "workshopId");
            Objects.requireNonNull(customerId, "customerId");
            Objects.requireNonNull(vehicleId, "vehicleId");
            Objects.requireNonNull(serviceOfferingId, "serviceOfferingId");
            Objects.requireNonNull(startsAt, "startsAt");
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("idempotencyKey is required");
            }
            if (requiredCapacity <= 0) throw new IllegalArgumentException("requiredCapacity must be positive");
            requiredResourceTags = requiredResourceTags == null ? List.of() : List.copyOf(requiredResourceTags);
        }
    }

    public record Result(UUID reservationId, UUID workshopId, UUID vehicleId,
                         UUID serviceOfferingId, Instant startsAt, int durationMinutes) { }

    private final ServiceOfferingCatalog catalogue;
    private final WorkshopReservationPort reservations;
    private final DomainClock clock;

    public BookWorkshopAppointment(ServiceOfferingCatalog catalogue, WorkshopReservationPort reservations,
                                   DomainClock clock) {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.reservations = Objects.requireNonNull(reservations, "reservations");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Uni<Result> execute(Command command) {
        Objects.requireNonNull(command, "command");
        return catalogue.find(command.tenantId(), command.serviceOfferingId())
                .flatMap(found -> {
                    ServiceOffering offering = found.orElseThrow(() ->
                            new IllegalArgumentException("Service offering not found"));
                    if (!offering.tenantId().equals(command.tenantId())) {
                        throw new SecurityException("Service offering does not belong to the requested tenant");
                    }
                    if (offering.status() != ServiceOfferingStatus.ACTIVE) {
                        throw new IllegalStateException("Service offering is not available for booking");
                    }
                    if (!command.startsAt().isAfter(clock.now())) {
                        throw new IllegalArgumentException("Appointment start must be in the future");
                    }
                    var reserve = new WorkshopReservationPort.ReserveCommand(
                            command.tenantId(), command.workshopId(), command.customerId(), command.vehicleId(),
                            offering.id().value(), command.startsAt(), offering.estimatedDurationMinutes(),
                            command.requiredCapacity(), command.requiredResourceTags(), command.idempotencyKey());
                    return reservations.reserve(reserve).map(reservationId ->
                            new Result(reservationId, command.workshopId(), command.vehicleId(),
                                    offering.id().value(), command.startsAt(), offering.estimatedDurationMinutes()));
                });
    }
}
