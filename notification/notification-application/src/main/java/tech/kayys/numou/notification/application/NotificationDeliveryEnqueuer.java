package tech.kayys.numou.notification.application;

import tech.kayys.numou.notification.application.port.NotificationDeliveryJobRepository;
import tech.kayys.numou.notification.application.port.PushDeviceRepository;
import tech.kayys.numou.notification.domain.Notification;
import tech.kayys.numou.notification.domain.NotificationDeliveryJob;
import tech.kayys.numou.notification.domain.PushDevice;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Creates durable per-device delivery jobs without sending provider traffic. */
public final class NotificationDeliveryEnqueuer {
    private final PushDeviceRepository devices;
    private final NotificationDeliveryJobRepository jobs;
    private final Clock clock;

    public NotificationDeliveryEnqueuer(PushDeviceRepository devices, NotificationDeliveryJobRepository jobs, Clock clock) {
        this.devices = Objects.requireNonNull(devices, "devices is required");
        this.jobs = Objects.requireNonNull(jobs, "jobs is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    /** Enqueues one idempotent job per currently active device. */
    public List<NotificationDeliveryJob> enqueue(Notification notification, String provider) {
        Objects.requireNonNull(notification, "notification is required");
        Objects.requireNonNull(provider, "provider is required");
        String normalizedProvider = provider.trim();
        if (normalizedProvider.isEmpty() || normalizedProvider.length() > NotificationDeliveryJob.MAX_PROVIDER_LENGTH)
            throw new IllegalArgumentException("provider must contain 1.." + NotificationDeliveryJob.MAX_PROVIDER_LENGTH + " characters");
        Instant now = Instant.now(clock);
        return devices.findActiveByRecipient(notification.tenantId(), notification.recipientId()).stream()
                .filter(PushDevice::isActive)
                .map(device -> jobs.enqueue(NotificationDeliveryJob.pending(
                        notification.tenantId(), notification.recipientId(), notification.id(), device.id(),
                        normalizedProvider, idempotencyKey(notification, device, normalizedProvider), now)))
                .toList();
    }

    private static String idempotencyKey(Notification notification, PushDevice device, String provider) {
        return "notification:" + notification.id() + ":device:" + device.id() + ":provider:" + provider;
    }
}
