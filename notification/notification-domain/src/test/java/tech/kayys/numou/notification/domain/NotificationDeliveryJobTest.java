package tech.kayys.numou.notification.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NotificationDeliveryJobTest {
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");

    private NotificationDeliveryJob pending() {
        return NotificationDeliveryJob.pending(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "FCM", "delivery:notification:device", NOW);
    }

    @Test
    void claimAndDeliverRequiresCurrentLeaseToken() {
        NotificationDeliveryJob job = pending();
        UUID lease = UUID.randomUUID();
        NotificationDeliveryJob claimed = job.claim(lease, NOW.plusSeconds(30), NOW);
        assertEquals(1, claimed.attemptCount());
        assertThrows(IllegalStateException.class,
                () -> claimed.delivered(UUID.randomUUID(), NOW.plusSeconds(1)));
        NotificationDeliveryJob delivered = claimed.delivered(lease, NOW.plusSeconds(1));
        assertEquals(NotificationDeliveryJob.Status.DELIVERED, delivered.status());
        assertNotNull(delivered.deliveredAt());
        assertNull(delivered.leaseToken());
    }

    @Test
    void expiredLeaseCanBeReclaimedAndOldWorkerIsFenced() {
        NotificationDeliveryJob first = pending().claim(
                UUID.randomUUID(), NOW.plusSeconds(10), NOW);
        UUID replacementToken = UUID.randomUUID();
        NotificationDeliveryJob reclaimed = first.claim(
                replacementToken, NOW.plusSeconds(30), NOW.plusSeconds(11));
        assertEquals(2, reclaimed.attemptCount());
        assertThrows(IllegalStateException.class,
                () -> reclaimed.retry(first.leaseToken(), NOW.plusSeconds(40), "timeout"));
        assertEquals(NotificationDeliveryJob.Status.RETRY_WAIT,
                reclaimed.retry(replacementToken, NOW.plusSeconds(40), "timeout").status());
    }

    @Test
    void cannotClaimNotYetDueOrTerminalJob() {
        NotificationDeliveryJob pending = NotificationDeliveryJob.pending(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "FCM", "key", NOW.plusSeconds(10));
        assertThrows(IllegalStateException.class,
                () -> pending.claim(UUID.randomUUID(), NOW.plusSeconds(30), NOW));
        NotificationDeliveryJob claimed = pending.claim(
                UUID.randomUUID(), NOW.plusSeconds(30), NOW.plusSeconds(10));
        NotificationDeliveryJob dead = claimed.dead(claimed.leaseToken(), "invalid_token");
        assertEquals(NotificationDeliveryJob.Status.DEAD, dead.status());
        assertThrows(IllegalStateException.class,
                () -> dead.claim(UUID.randomUUID(), NOW.plusSeconds(50), NOW.plusSeconds(20)));
    }

    @Test
    void errorCodeIsBoundedAndLeaseStateIsValidated() {
        assertThrows(IllegalArgumentException.class, () -> pending().claim(
                UUID.randomUUID(), NOW, NOW));
        NotificationDeliveryJob claimed = pending().claim(
                UUID.randomUUID(), NOW.plusSeconds(30), NOW);
        String oversizedErrorCode = "x".repeat(NotificationDeliveryJob.MAX_ERROR_CODE_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, () -> claimed.retry(
                claimed.leaseToken(), NOW.plusSeconds(10), oversizedErrorCode));
        assertThrows(IllegalStateException.class, () -> new NotificationDeliveryJob(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "FCM", "key", NotificationDeliveryJob.Status.IN_FLIGHT, 1,
                NOW, NOW, null, null, null, ""));
    }
}
