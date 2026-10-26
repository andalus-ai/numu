package tech.kayys.numou.vehicleservice.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.vehicleservice.domain.ServiceCategory;
import tech.kayys.numou.vehicleservice.domain.ServiceOffering;
import tech.kayys.numou.vehicleservice.domain.ServiceOfferingId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FindWorkshopAppointmentSlotsTest {
    @Test
    void queriesCapacityWithTheCatalogueDurationAndResourceConstraints() {
        String tenant = "tenant-a";
        UUID workshop = UUID.randomUUID();
        ServiceOfferingId id = ServiceOfferingId.generate();
        ServiceOffering offering = ServiceOffering.create(id, tenant, "DIAG", "Diagnostics", "",
                ServiceCategory.DIAGNOSTICS, 60);
        offering.activate();
        AtomicReference<WorkshopReservationPort.SlotRequest> received = new AtomicReference<>();
        Instant slot = Instant.parse("2026-10-27T09:00:00Z");
        WorkshopReservationPort port = new WorkshopReservationPort() {
            @Override public Uni<List<Instant>> availableSlots(SlotRequest request) {
                received.set(request);
                return Uni.createFrom().item(List.of(slot));
            }
            @Override public Uni<UUID> reserve(ReserveCommand command) {
                return Uni.createFrom().item(UUID.randomUUID());
            }
            @Override public Uni<Void> cancel(String tenantId, UUID reservationId, String reason) {
                return Uni.createFrom().voidItem();
            }
            @Override public Uni<Void> reschedule(String tenantId, UUID reservationId, Instant newStart, int durationMinutes) {
                return Uni.createFrom().voidItem();
            }
        };
        FindWorkshopAppointmentSlots useCase = new FindWorkshopAppointmentSlots(
                (tenantId, offeringId) -> Uni.createFrom().item(Optional.of(offering)), port);

        List<Instant> slots = useCase.execute(new FindWorkshopAppointmentSlots.Query(tenant, workshop, id,
                LocalDate.parse("2026-10-27"), 1, List.of("diagnostic-bay"))).await().indefinitely();

        assertThat(slots).containsExactly(slot);
        assertThat(received.get().durationMinutes()).isEqualTo(60);
        assertThat(received.get().requiredResourceTags()).containsExactly("diagnostic-bay");
        assertThat(received.get().requiredCapacity()).isEqualTo(1);
    }
}
