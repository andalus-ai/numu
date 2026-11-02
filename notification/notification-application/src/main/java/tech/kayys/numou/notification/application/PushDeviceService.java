package tech.kayys.numou.notification.application;

import tech.kayys.numou.notification.application.port.PushDeviceRepository;
import tech.kayys.numou.notification.domain.PushDevice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Use cases for push-device registration. The authenticated host adapter must
 * derive tenantId and recipientId from a verified principal before calling
 * these methods; identifiers in a request body are not authorization.
 */
public final class PushDeviceService {
    private final PushDeviceRepository repository;
    private final Clock clock;

    public PushDeviceService(PushDeviceRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    /**
     * Internal token-bearing registration result. Management endpoints should
     * use registerSummary so provider credentials cannot be serialized.
     */
    @Deprecated(forRemoval = false)
    public PushDevice register(
            UUID tenantId,
            UUID recipientId,
            String token,
            PushDevice.Platform platform,
            String appInstance) {
        requireScope(tenantId, recipientId);
        Instant now = Instant.now(clock);
        return repository.registerOrRefresh(
                PushDevice.register(tenantId, recipientId, token, platform, appInstance, now));
    }

    /** Registers or refreshes a device and returns only safe metadata. */
    public PushDeviceSummary registerSummary(
            UUID tenantId,
            UUID recipientId,
            String token,
            PushDevice.Platform platform,
            String appInstance) {
        requireScope(tenantId, recipientId);
        Instant now = Instant.now(clock);
        return repository.registerOrRefreshSummary(
                PushDevice.register(tenantId, recipientId, token, platform, appInstance, now));
    }

    /**
     * Internal delivery-oriented view. Do not serialize this token-bearing
     * entity from an HTTP/API management endpoint; use activeDeviceSummaries.
     */
    @Deprecated(forRemoval = false)
    public List<PushDevice> activeDevices(UUID tenantId, UUID recipientId) {
        requireScope(tenantId, recipientId);
        return List.copyOf(repository.findActiveByRecipient(tenantId, recipientId));
    }

    /**
     * Internal token-bearing result. Management endpoints should use
     * unregisterSummary so the response cannot expose provider credentials.
     */
    @Deprecated(forRemoval = false)
    public Optional<PushDevice> unregister(UUID tenantId, UUID recipientId, UUID deviceId) {
        requireScope(tenantId, recipientId);
        Objects.requireNonNull(deviceId, "deviceId is required");
        return repository.disable(tenantId, recipientId, deviceId, Instant.now(clock));
    }

    /** Returns only safe device metadata; provider credentials are never loaded for JDBC. */
    public List<PushDeviceSummary> activeDeviceSummaries(UUID tenantId, UUID recipientId) {
        requireScope(tenantId, recipientId);
        return List.copyOf(repository.findActiveSummaries(tenantId, recipientId));
    }

    /** Disables a device and returns a secret-free management projection. */
    public Optional<PushDeviceSummary> unregisterSummary(
            UUID tenantId, UUID recipientId, UUID deviceId) {
        requireScope(tenantId, recipientId);
        Objects.requireNonNull(deviceId, "deviceId is required");
        return repository.disableSummary(tenantId, recipientId, deviceId, Instant.now(clock));
    }

    private static void requireScope(UUID tenantId, UUID recipientId) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(recipientId, "recipientId is required");
    }
}
