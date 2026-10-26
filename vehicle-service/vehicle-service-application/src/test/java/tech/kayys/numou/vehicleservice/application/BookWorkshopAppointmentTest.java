package tech.kayys.numou.vehicleservice.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.vehicleservice.domain.ServiceCategory;
import tech.kayys.numou.vehicleservice.domain.ServiceOffering;
import tech.kayys.numou.vehicleservice.domain.ServiceOfferingId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookWorkshopAppointmentTest {
    private final String tenant = "tenant-a";
    private final UUID workshopId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID vehicleId = UUID.randomUUID();
    private final ServiceOfferingId offeringId = ServiceOfferingId.generate();
    private final Instant now = Instant.parse("2026-10-26T08:00:00Z");
    private final DomainClock clock = () -> now;
    private final AtomicReference<WorkshopReservationPort.ReserveCommand> received = new AtomicReference<>();

    private ServiceOffering activeOffering() {
        ServiceOffering offering = ServiceOffering.create(offeringId, tenant, "OIL-CHANGE", "Oil change", "",
                ServiceCategory.ROUTINE_MAINTENANCE, 45);
        offering.activate();
        return offering;
    }

    private WorkshopReservationPort reservationPort() {
        return new WorkshopReservationPort() {
            @Override public Uni<List<Instant>> availableSlots(SlotRequest request) {
                return Uni.createFrom().item(List.of());
            }
            @Override public Uni<UUID> reserve(ReserveCommand command) {
                received.set(command);
                return Uni.createFrom().item(UUID.randomUUID());
            }
            @Override public Uni<Void> cancel(String tenantId, UUID reservationId, String reason) {
                return Uni.createFrom().voidItem();
            }
            @Override public Uni<Void> reschedule(String tenantId, UUID reservationId, Instant newStart, int durationMinutes) {
                return Uni.createFrom().voidItem();
            }
        };
    }

    @Test
    void booksUsingCatalogueDurationAndPassesIdempotencyAndResourceRequirements() {
        ServiceOffering offering = activeOffering();
        BookWorkshopAppointment useCase = new BookWorkshopAppointment(
                (tenantId, id) -> Uni.createFrom().item(Optional.of(offering)), reservationPort(), clock);
        Instant startsAt = now.plusSeconds(3600);
        var result = useCase.execute(new BookWorkshopAppointment.Command(tenant, workshopId, customerId,
                vehicleId, offeringId, startsAt, "request-123", 1, List.of("lift"))).await().indefinitely();

        assertThat(result.durationMinutes()).isEqualTo(45);
        assertThat(received.get().durationMinutes()).isEqualTo(45);
        assertThat(received.get().requiredResourceTags()).containsExactly("lift");
        assertThat(received.get().idempotencyKey()).isEqualTo("request-123");
        assertThat(result.reservationId()).isNotNull();
    }

    @Test
    void rejectsInactiveOfferingBeforeReservation() {
        ServiceOffering offering = ServiceOffering.create(offeringId, tenant, "OIL-CHANGE", "Oil change", "",
                ServiceCategory.ROUTINE_MAINTENANCE, 45);
        BookWorkshopAppointment useCase = new BookWorkshopAppointment(
                (tenantId, id) -> Uni.createFrom().item(Optional.of(offering)), reservationPort(), clock);

        assertThatThrownBy(() -> useCase.execute(new BookWorkshopAppointment.Command(tenant, workshopId,
                customerId, vehicleId, offeringId, now.plusSeconds(3600), "request-124", 1, List.of()))
                .await().indefinitely()).isInstanceOf(IllegalStateException.class);
        assertThat(received.get()).isNull();
    }

    @Test
    void rejectsMissingOffering() {
        BookWorkshopAppointment useCase = new BookWorkshopAppointment(
                (tenantId, id) -> Uni.createFrom().item(Optional.empty()), reservationPort(), clock);

        assertThatThrownBy(() -> useCase.execute(new BookWorkshopAppointment.Command(tenant, workshopId,
                customerId, vehicleId, offeringId, now.plusSeconds(3600), "request-125", 1, List.of()))
                .await().indefinitely()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAppointmentsThatAreNotStrictlyInTheFuture() {
        ServiceOffering offering = activeOffering();
        BookWorkshopAppointment useCase = new BookWorkshopAppointment(
                (tenantId, id) -> Uni.createFrom().item(Optional.of(offering)), reservationPort(), clock);

        assertThatThrownBy(() -> useCase.execute(new BookWorkshopAppointment.Command(tenant, workshopId,
                customerId, vehicleId, offeringId, now, "request-126", 1, List.of()))
                .await().indefinitely()).isInstanceOf(IllegalArgumentException.class);
        assertThat(received.get()).isNull();
    }
}
