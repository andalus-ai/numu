package tech.kayys.numou.notification.application;

import tech.kayys.numou.notification.application.port.NotificationRequestContext;
import tech.kayys.numou.notification.domain.Notification;
import tech.kayys.numou.notification.domain.PushDevice;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Actor-scoped use cases for host adapters.
 *
 * <p>Unlike the lower-level services, these methods do not accept tenant or
 * recipient identifiers from callers. Every operation obtains its scope from
 * the host-provided verified request context. Device-management responses are
 * metadata-only projections and never contain provider credentials.
 */
public final class AuthenticatedNotificationApi {
    private final NotificationService notifications;
    private final PushDeviceService devices;
    private final NotificationRequestContext requestContext;

    public AuthenticatedNotificationApi(
            NotificationService notifications,
            PushDeviceService devices,
            NotificationRequestContext requestContext) {
        this.notifications = Objects.requireNonNull(notifications, "notifications is required");
        this.devices = Objects.requireNonNull(devices, "devices is required");
        this.requestContext = Objects.requireNonNull(requestContext, "requestContext is required");
    }

    public List<Notification> inbox(int limit, Instant beforeCreatedAt) {
        var actor = requireActor();
        return notifications.inbox(actor.tenantId(), actor.recipientId(), limit, beforeCreatedAt);
    }

    public Optional<Notification> markRead(UUID notificationId) {
        Objects.requireNonNull(notificationId, "notificationId is required");
        var actor = requireActor();
        return notifications.markRead(actor.tenantId(), actor.recipientId(), notificationId);
    }

    public DeviceView registerDevice(RegisterDeviceRequest request) {
        Objects.requireNonNull(request, "request is required");
        var actor = requireActor();
        return DeviceView.from(devices.registerSummary(actor.tenantId(), actor.recipientId(),
                request.token(), request.platform(), request.appInstance()));
    }

    public List<DeviceView> activeDevices() {
        var actor = requireActor();
        return devices.activeDeviceSummaries(actor.tenantId(), actor.recipientId()).stream()
                .map(DeviceView::from).toList();
    }

    public Optional<DeviceView> unregisterDevice(UUID deviceId) {
        Objects.requireNonNull(deviceId, "deviceId is required");
        var actor = requireActor();
        return devices.unregisterSummary(actor.tenantId(), actor.recipientId(), deviceId)
                .map(DeviceView::from);
    }

    private NotificationRequestContext.Actor requireActor() {
        return requestContext.currentActor()
                .orElseThrow(UnauthenticatedNotificationRequestException::new);
    }

    /** Registration input deliberately contains no tenant or recipient fields. */
    public record RegisterDeviceRequest(
            String token,
            PushDevice.Platform platform,
            String appInstance) {
        public RegisterDeviceRequest {
            Objects.requireNonNull(token, "token is required");
            Objects.requireNonNull(platform, "platform is required");
        }
    }

    /** Safe device response; provider credentials and internal tenant scope are never exposed. */
    public record DeviceView(
            UUID id,
            PushDevice.Platform platform,
            String appInstance,
            Instant createdAt,
            Instant updatedAt,
            Instant disabledAt) {
        static DeviceView from(PushDeviceSummary summary) {
            return new DeviceView(summary.id(), summary.platform(), summary.appInstance(),
                    summary.createdAt(), summary.updatedAt(), summary.disabledAt());
        }

        public boolean active() {
            return disabledAt == null;
        }
    }

    /** Host REST adapters should map this to HTTP 401. */
    public static final class UnauthenticatedNotificationRequestException extends RuntimeException {
        public UnauthenticatedNotificationRequestException() {
            super("An authenticated notification actor is required");
        }
    }
}
