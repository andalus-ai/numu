package tech.kayys.numou.notification.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.notification.application.port.NotificationDeliveryJobRepository;
import tech.kayys.numou.notification.application.port.PushDeviceRepository;
import tech.kayys.numou.notification.domain.Notification;
import tech.kayys.numou.notification.domain.NotificationDeliveryJob;
import tech.kayys.numou.notification.domain.PushDevice;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NotificationDeliveryEnqueuerTest {
    @Test void enqueuesOnlyActiveDevicesWithStableIdempotencyKeys() {
        Instant now = Instant.parse("2026-10-29T00:00:00Z");
        UUID tenant = UUID.randomUUID(), recipient = UUID.randomUUID();
        Notification notification = Notification.create(tenant, recipient, "task.assigned", "Task assigned",
                "Details", "task:123", "task-assigned-1", now);
        PushDevice active = PushDevice.register(tenant, recipient, "token", PushDevice.Platform.ANDROID, "", now);
        PushDevice disabled = PushDevice.register(tenant, recipient, "token2", PushDevice.Platform.IOS, "", now).disable(now.plusSeconds(1));
        var devices = new RecordingDevices(List.of(active, disabled));
        var jobs = new RecordingJobs();
        var enqueuer = new NotificationDeliveryEnqueuer(devices, jobs, Clock.fixed(now, ZoneOffset.UTC));
        List<NotificationDeliveryJob> first = enqueuer.enqueue(notification, "FCM");
        List<NotificationDeliveryJob> second = enqueuer.enqueue(notification, "FCM");
        assertEquals(1, first.size());
        assertEquals(1, second.size());
        assertEquals(first.getFirst().id(), second.getFirst().id());
        assertEquals(first.getFirst().idempotencyKey(), second.getFirst().idempotencyKey());
        assertEquals(1, jobs.stored.size());
    }
    private static final class RecordingDevices implements PushDeviceRepository {
        private final List<PushDevice> devices;
        RecordingDevices(List<PushDevice> devices) { this.devices = devices; }
        @Override public PushDevice registerOrRefresh(PushDevice device) { return device; }
        @Override public List<PushDevice> findActiveByRecipient(UUID tenantId, UUID recipientId) {
            return devices.stream().filter(d -> d.tenantId().equals(tenantId) && d.recipientId().equals(recipientId) && d.isActive()).toList();
        }
        @Override public Optional<PushDevice> disable(UUID tenantId, UUID recipientId, UUID deviceId, Instant disabledAt) { return Optional.empty(); }
    }
    private static final class RecordingJobs implements NotificationDeliveryJobRepository {
        private final List<NotificationDeliveryJob> stored = new ArrayList<>();
        @Override public NotificationDeliveryJob enqueue(NotificationDeliveryJob job) {
            return stored.stream().filter(old -> old.tenantId().equals(job.tenantId()) && old.idempotencyKey().equals(job.idempotencyKey()))
                    .findFirst().orElseGet(() -> { stored.add(job); return job; });
        }
        @Override public List<NotificationDeliveryJob> claimDue(Instant now, Instant leaseExpiry, int limit, UUID leaseToken) { return List.of(); }
        @Override public Optional<NotificationDeliveryJob> markDelivered(UUID tenantId, UUID jobId, UUID leaseToken, Instant now) { return Optional.empty(); }
        @Override public Optional<NotificationDeliveryJob> scheduleRetry(UUID tenantId, UUID jobId, UUID leaseToken, Instant retryAt, String safeErrorCode) { return Optional.empty(); }
        @Override public Optional<NotificationDeliveryJob> markDead(UUID tenantId, UUID jobId, UUID leaseToken, String safeErrorCode) { return Optional.empty(); }
    }
}
