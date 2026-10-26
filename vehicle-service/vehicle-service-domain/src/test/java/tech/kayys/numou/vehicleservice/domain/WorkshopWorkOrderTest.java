package tech.kayys.numou.vehicleservice.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class WorkshopWorkOrderTest {
    private static final Instant CREATED_AT = Instant.parse("2026-10-26T08:00:00Z");

    private static WorkshopWorkOrder draft() {
        return WorkshopWorkOrder.create(WorkshopWorkOrderId.generate(), "tenant-a", UUID.randomUUID(),
                UUID.randomUUID(), ServiceOfferingId.generate(), UUID.randomUUID(),
                "Engine warning light is on", CREATED_AT);
    }

    @Test
    void enforcesApprovalAssignmentAndCompletionLifecycle() {
        WorkshopWorkOrder order = draft();
        assertThatIllegalStateException().isThrownBy(() -> order.start(CREATED_AT));

        order.requestCustomerApproval();
        order.approveByCustomer();
        order.schedule();
        UUID technicianId = UUID.randomUUID();
        order.assignTechnician(technicianId);
        order.start(Instant.parse("2026-10-26T09:00:00Z"));
        order.hold();
        order.resume();
        order.markReadyForPickup();
        order.complete(Instant.parse("2026-10-26T10:00:00Z"));

        assertThat(order.status()).isEqualTo(WorkshopWorkOrderStatus.COMPLETED);
        assertThat(order.assignedTechnicianId()).isEqualTo(technicianId);
        assertThat(order.startedAt()).isEqualTo(Instant.parse("2026-10-26T09:00:00Z"));
        assertThat(order.completedAt()).isEqualTo(Instant.parse("2026-10-26T10:00:00Z"));
        assertThatIllegalStateException().isThrownBy(() -> order.cancel("too late"));
        assertThatIllegalStateException().isThrownBy(() -> order.assignTechnician(UUID.randomUUID()));
    }

    @Test
    void cancellationRequiresReasonAndCannotBeRepeatedAfterTerminalState() {
        WorkshopWorkOrder order = draft();
        order.cancel("Customer requested cancellation");

        assertThat(order.status()).isEqualTo(WorkshopWorkOrderStatus.CANCELLED);
        assertThat(order.cancellationReason()).isEqualTo("Customer requested cancellation");
        assertThatIllegalStateException().isThrownBy(() -> order.cancel("again"));
    }

    @Test
    void cannotStartWithoutAssignedTechnicianEvenAfterApproval() {
        WorkshopWorkOrder order = draft();
        order.requestCustomerApproval();
        order.approveByCustomer();

        assertThatIllegalStateException().isThrownBy(() -> order.start(CREATED_AT.plusSeconds(60)));
    }

    @Test
    void rejectsLifecycleTimesThatMoveBackwards() {
        WorkshopWorkOrder order = draft();
        order.requestCustomerApproval();
        order.approveByCustomer();
        order.assignTechnician(UUID.randomUUID());

        assertThatIllegalArgumentException().isThrownBy(() -> order.start(CREATED_AT.minusSeconds(1)));
        order.start(CREATED_AT.plusSeconds(60));
        order.markReadyForPickup();
        assertThatIllegalArgumentException().isThrownBy(() -> order.complete(CREATED_AT));
        assertThat(order.status()).isEqualTo(WorkshopWorkOrderStatus.READY_FOR_PICKUP);
    }
}
