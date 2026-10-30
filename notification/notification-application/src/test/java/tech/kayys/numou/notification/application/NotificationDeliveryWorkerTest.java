package tech.kayys.numou.notification.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.notification.application.port.*;
import tech.kayys.numou.notification.domain.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NotificationDeliveryWorkerTest {
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");

    @Test
    void deliversWithTenantScopedContentAndMarksJobDelivered() {
        UUID tenant = UUID.randomUUID(), recipient = UUID.randomUUID();
        Notification notification = Notification.create(tenant, recipient, "task.assigned",
                "Assigned", "Private content", "task:1", "notification-1", NOW);
        PushDevice device = PushDevice.register(tenant, recipient, "secret-provider-token",
                PushDevice.Platform.ANDROID, "app", NOW);
        NotificationDeliveryJob pending = NotificationDeliveryJob.pending(tenant, recipient,
                notification.id(), device.id(), "TEST", "job-1", NOW);
        RecordingJobs jobs = new RecordingJobs(pending);
        RecordingProvider provider = new RecordingProvider();
        NotificationRepository notificationRepository = new NotificationRepository() {
            public Notification createIfAbsent(Notification n) { return n; }
            public List<Notification> findInbox(UUID t, UUID r, int limit, Instant before) { return List.of(); }
            public Optional<Notification> findById(UUID t, UUID r, UUID id) {
                return t.equals(tenant) && r.equals(recipient) && id.equals(notification.id())
                        ? Optional.of(notification) : Optional.empty();
            }
            public Optional<Notification> markRead(UUID t, UUID r, UUID id, Instant at) { return Optional.empty(); }
        };
        PushDeviceRepository deviceRepository = new PushDeviceRepository() {
            public PushDevice registerOrRefresh(PushDevice d) { return d; }
            public List<PushDevice> findActiveByRecipient(UUID t, UUID r) {
                return t.equals(tenant) && r.equals(recipient) ? List.of(device) : List.of();
            }
            public Optional<PushDevice> disable(UUID t, UUID r, UUID id, Instant at) { return Optional.empty(); }
        };
        var worker = new NotificationDeliveryWorker(jobs, notificationRepository, deviceRepository, Map.of("TEST", provider),
                new NotificationDeliveryRetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(10), 0),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30), 10);

        var result = worker.processBatch();

        assertEquals(new NotificationDeliveryWorker.BatchResult(1, 1, 0, 0), result);
        assertEquals(tenant, provider.request.tenantId());
        assertEquals(notification.id(), provider.request.notificationId());
        assertEquals("secret-provider-token", provider.request.deviceToken());
        assertFalse(provider.request.toString().contains("secret-provider-token"));
        assertFalse(provider.request.toString().contains("Private content"));
        assertTrue(jobs.delivered);
    }

    private static final class RecordingProvider implements NotificationDeliveryProvider {
        private DeliveryRequest request;
        public String providerCode() { return "TEST"; }
        public DeliveryResult deliver(DeliveryRequest request) {
            this.request = request;
            return DeliveryResult.success();
        }
    }

    private static final class RecordingJobs implements NotificationDeliveryJobRepository {
        private final NotificationDeliveryJob pending;
        private NotificationDeliveryJob claimed;
        private UUID lease;
        private boolean delivered;
        RecordingJobs(NotificationDeliveryJob pending) { this.pending = pending; }
        public NotificationDeliveryJob enqueue(NotificationDeliveryJob job) { return job; }
        public List<NotificationDeliveryJob> claimDue(Instant now, Instant expiry, int limit, UUID leaseToken) {
            assertEquals(NOW, now);
            assertEquals(NOW.plusSeconds(30), expiry);
            assertEquals(10, limit);
            lease = leaseToken;
            claimed = pending.claim(leaseToken, expiry, now);
            return List.of(claimed);
        }
        public Optional<NotificationDeliveryJob> markDelivered(UUID tenant, UUID id, UUID token, Instant now) {
            assertEquals(claimed.tenantId(), tenant);
            assertEquals(claimed.id(), id);
            assertEquals(lease, token);
            delivered = true;
            return Optional.of(claimed.delivered(token, now));
        }
        public Optional<NotificationDeliveryJob> scheduleRetry(UUID t, UUID id, UUID token, Instant at, String code) {
            return Optional.empty();
        }
        public Optional<NotificationDeliveryJob> markDead(UUID t, UUID id, UUID token, String code) {
            return Optional.empty();
        }
    }
}
