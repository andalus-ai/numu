package tech.kayys.numou.queuemanagement.postgres;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.queuemanagement.domain.QueueEntry;
import tech.kayys.numou.queuemanagement.domain.QueueEntryId;
import tech.kayys.numou.queuemanagement.domain.QueueEntryStatus;
import tech.kayys.numou.queuemanagement.domain.QueueId;
import tech.kayys.numou.queuemanagement.domain.QueueProfile;
import tech.kayys.numou.queuemanagement.domain.QueueSourceReference;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QueueEntryIdempotencyResultCodecTest {
    private final QueueEntryIdempotencyResultCodec codec = new QueueEntryIdempotencyResultCodec();

    @Test
    void roundTripsCompleteLifecycleStateWithoutReplayingTransitions() {
        Instant entered = Instant.parse("2026-10-29T08:00:00Z");
        Instant called = entered.plusSeconds(60);
        Instant serving = called.plusSeconds(30);
        Instant completed = serving.plusSeconds(300);
        QueueEntry original = QueueEntry.reconstitute(
                QueueEntryId.of(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                TenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000002")),
                QueueId.of(UUID.fromString("00000000-0000-0000-0000-000000000003")),
                QueueProfile.WAITING_SERVICE,
                new QueueSourceReference("crm", "appointment", "apt-42"),
                75, entered, completed, QueueEntryStatus.COMPLETED,
                called, serving, completed, 1);

        QueueEntry restored = codec.decode(codec.encode(original));

        assertEquals(original.id(), restored.id());
        assertEquals(original.tenantId(), restored.tenantId());
        assertEquals(original.queueId(), restored.queueId());
        assertEquals(original.profile(), restored.profile());
        assertEquals(original.source(), restored.source());
        assertEquals(original.priority(), restored.priority());
        assertEquals(original.status(), restored.status());
        assertEquals(original.enteredAt(), restored.enteredAt());
        assertEquals(original.updatedAt(), restored.updatedAt());
        assertEquals(original.calledAt(), restored.calledAt());
        assertEquals(original.servingStartedAt(), restored.servingStartedAt());
        assertEquals(original.completedAt(), restored.completedAt());
        assertEquals(original.callCount(), restored.callCount());
    }

    @Test
    void roundTripsNullableLifecycleTimestamps() {
        QueueEntry original = QueueEntry.reconstitute(
                QueueEntryId.generate(), TenantId.generate(), QueueId.generate(),
                QueueProfile.OPERATIONAL_WORK,
                new QueueSourceReference("field-service", "work-order", "wo-9"),
                0, Instant.parse("2026-10-29T08:00:00Z"),
                Instant.parse("2026-10-29T08:00:00Z"), QueueEntryStatus.WAITING,
                null, null, null, 0);

        QueueEntry restored = codec.decode(codec.encode(original));

        assertEquals(QueueEntryStatus.WAITING, restored.status());
        assertNull(restored.calledAt());
        assertNull(restored.servingStartedAt());
        assertNull(restored.completedAt());
    }
}
