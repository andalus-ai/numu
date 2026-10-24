package tech.kayys.numou.courier.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryTaskTest {
    @Test
    void recordsAttemptThenProofOfDeliveryAndPreventsMutationAfterCompletion() {
        Instant now = Instant.parse("2026-10-24T00:00:00Z");
        DeliveryTask task = DeliveryTask.assign(DeliveryTaskId.generate(), TenantId.generate(),
                ShipmentId.generate(), "courier-17", "dispatcher-2", now);
        task.startDelivery(now.plusSeconds(1));
        task.recordFailedAttempt("Recipient unavailable", now.plusSeconds(2));
        task.recordProofOfDelivery("A. Recipient", "media://pod/tenant-scoped/object-42", now.plusSeconds(3));
        assertEquals(DeliveryTaskStatus.DELIVERED, task.status());
        assertEquals("A. Recipient", task.recipientName());
        assertNotNull(task.proofReference());
        assertThrows(IllegalStateException.class,
                () -> task.recordFailedAttempt("late replay", now.plusSeconds(4)));
    }

    @Test
    void rejectsMissingProofAndBackwardsTime() {
        Instant now = Instant.parse("2026-10-24T00:00:00Z");
        DeliveryTask task = DeliveryTask.assign(DeliveryTaskId.generate(), TenantId.generate(),
                ShipmentId.generate(), "courier-17", "dispatcher-2", now);
        assertThrows(IllegalStateException.class,
                () -> task.recordProofOfDelivery("Recipient", "proof://x", now));
        task.startDelivery(now.plusSeconds(2));
        assertThrows(IllegalArgumentException.class, () -> task.recordFailedAttempt("No answer", now.plusSeconds(1)));
    }
}
