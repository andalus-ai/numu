package tech.kayys.numou.notification.application;

import tech.kayys.numou.notification.application.port.NotificationDeliveryJobRepository;
import tech.kayys.numou.notification.application.port.NotificationDeliveryProvider;
import tech.kayys.numou.notification.application.port.NotificationRepository;
import tech.kayys.numou.notification.application.port.PushDeviceRepository;
import tech.kayys.numou.notification.domain.Notification;
import tech.kayys.numou.notification.domain.NotificationDeliveryJob;
import tech.kayys.numou.notification.domain.PushDevice;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Bounded transport-neutral worker; a host scheduler must explicitly invoke it. */
public final class NotificationDeliveryWorker {
    private static final String DEVICE_UNAVAILABLE = "device_unavailable";
    private static final String NOTIFICATION_UNAVAILABLE = "notification_unavailable";
    private static final String PROVIDER_REJECTED = "provider_rejected";
    private final NotificationDeliveryJobRepository jobs;
    private final NotificationRepository notifications;
    private final PushDeviceRepository devices;
    private final Map<String, NotificationDeliveryProvider> providers;
    private final NotificationDeliveryRetryPolicy retryPolicy;
    private final Clock clock;
    private final Duration leaseDuration;
    private final int batchSize;

    public NotificationDeliveryWorker(NotificationDeliveryJobRepository jobs, NotificationRepository notifications,
            PushDeviceRepository devices, NotificationDeliveryProvider provider,
            NotificationDeliveryRetryPolicy retryPolicy, Clock clock, Duration leaseDuration, int batchSize) {
        this(jobs, notifications, devices, Map.of(
                Objects.requireNonNull(provider, "provider is required").providerCode(), provider),
                retryPolicy, clock, leaseDuration, batchSize);
    }

    /**
     * Creates a worker capable of processing jobs for multiple configured providers.
     * Job.provider remains the dispatch key, so existing queued jobs retain their
     * provider affinity during rolling deployments and provider migrations.
     */
    public NotificationDeliveryWorker(NotificationDeliveryJobRepository jobs, NotificationRepository notifications,
            PushDeviceRepository devices, Map<String, NotificationDeliveryProvider> providers,
            NotificationDeliveryRetryPolicy retryPolicy, Clock clock, Duration leaseDuration, int batchSize) {
        this.jobs = Objects.requireNonNull(jobs, "jobs is required");
        this.notifications = Objects.requireNonNull(notifications, "notifications is required");
        this.devices = Objects.requireNonNull(devices, "devices is required");
        Objects.requireNonNull(providers, "providers is required");
        if (providers.isEmpty()) throw new IllegalArgumentException("at least one provider is required");
        Map<String, NotificationDeliveryProvider> configured = new java.util.HashMap<>();
        providers.forEach((code, implementation) -> {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("provider key is required");
            Objects.requireNonNull(implementation, "provider implementation is required");
            if (!code.equals(implementation.providerCode())) {
                throw new IllegalArgumentException("provider map key must match providerCode");
            }
            if (configured.putIfAbsent(code, implementation) != null) {
                throw new IllegalArgumentException("duplicate providerCode: " + code);
            }
        });
        this.providers = Map.copyOf(configured);
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration is required");
        if (leaseDuration.isNegative() || leaseDuration.isZero()) throw new IllegalArgumentException("leaseDuration must be positive");
        if (batchSize < 1 || batchSize > 500) throw new IllegalArgumentException("batchSize must be 1..500");
        this.batchSize = batchSize;
    }

    public BatchResult processBatch() {
        Instant startedAt = Instant.now(clock);
        UUID leaseToken = UUID.randomUUID();
        List<NotificationDeliveryJob> claimed = jobs.claimDue(startedAt, startedAt.plus(leaseDuration), batchSize, leaseToken);
        int delivered = 0, retrying = 0, dead = 0;
        for (NotificationDeliveryJob job : claimed) {
            switch (process(job, leaseToken)) {
                case DELIVERED -> delivered++;
                case RETRYING -> retrying++;
                case DEAD -> dead++;
            }
        }
        return new BatchResult(claimed.size(), delivered, retrying, dead);
    }

    private Outcome process(NotificationDeliveryJob job, UUID leaseToken) {
        Instant now = Instant.now(clock);
        NotificationDeliveryProvider provider = providers.get(job.provider());
        if (provider == null) return dead(job, leaseToken, "provider_unavailable");
        Notification notification = notifications.findById(job.tenantId(), job.recipientId(), job.notificationId()).orElse(null);
        if (notification == null) return dead(job, leaseToken, NOTIFICATION_UNAVAILABLE);
        PushDevice device = devices.findActiveByRecipient(job.tenantId(), job.recipientId()).stream()
                .filter(candidate -> candidate.id().equals(job.deviceId()) && candidate.isActive())
                .findFirst().orElse(null);
        if (device == null) return dead(job, leaseToken, DEVICE_UNAVAILABLE);

        NotificationDeliveryProvider.DeliveryResult result;
        try {
            result = Objects.requireNonNull(provider.deliver(new NotificationDeliveryProvider.DeliveryRequest(
                    job.tenantId(), notification.id(), device.id(), device.token(),
                    notification.title(), notification.body(), notification.sourceReference())), "provider result is required");
        } catch (RuntimeException providerFailure) {
            return failure(job, leaseToken, "provider_error", true, now);
        }
        if (result.accepted()) {
            return jobs.markDelivered(job.tenantId(), job.id(), leaseToken, Instant.now(clock)).isPresent()
                    ? Outcome.DELIVERED : Outcome.DEAD;
        }
        return failure(job, leaseToken, result.safeErrorCode().isEmpty() ? PROVIDER_REJECTED : result.safeErrorCode(),
                result.retryable(), now);
    }

    private Outcome failure(NotificationDeliveryJob job, UUID leaseToken, String code, boolean retryable, Instant now) {
        if (retryable && !retryPolicy.exhausted(job.attemptCount())) {
            Instant retryAt = now.plus(retryPolicy.delayForAttempt(job.attemptCount()));
            return jobs.scheduleRetry(job.tenantId(), job.id(), leaseToken, retryAt, code).isPresent()
                    ? Outcome.RETRYING : Outcome.DEAD;
        }
        return dead(job, leaseToken, code);
    }

    private Outcome dead(NotificationDeliveryJob job, UUID leaseToken, String code) {
        jobs.markDead(job.tenantId(), job.id(), leaseToken, code);
        return Outcome.DEAD;
    }

    private enum Outcome { DELIVERED, RETRYING, DEAD }
    public record BatchResult(int claimed, int delivered, int retrying, int dead) {
        public BatchResult {
            if (claimed < 0 || delivered < 0 || retrying < 0 || dead < 0 || delivered + retrying + dead > claimed)
                throw new IllegalArgumentException("invalid batch result counts");
        }
    }
}
