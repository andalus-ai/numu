package tech.kayys.numou.notification.application;

import tech.kayys.numou.notification.domain.PushDevice;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Safe management projection for a registered device. It deliberately has no
 * provider token, encrypted token, key identifier, or token fingerprint.
 */
public record PushDeviceSummary(
        UUID id,
        UUID tenantId,
        UUID recipientId,
        PushDevice.Platform platform,
        String appInstance,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt) {
    public PushDeviceSummary {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(recipientId, "recipientId is required");
        Objects.requireNonNull(platform, "platform is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        appInstance = appInstance == null ? "" : appInstance;
    }

    public boolean active() {
        return disabledAt == null;
    }

    public static PushDeviceSummary from(PushDevice device) {
        Objects.requireNonNull(device, "device is required");
        return new PushDeviceSummary(device.id(), device.tenantId(), device.recipientId(),
                device.platform(), device.appInstance(), device.createdAt(), device.updatedAt(),
                device.disabledAt());
    }
}
