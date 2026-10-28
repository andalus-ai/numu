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
 * the host-provided verified request context.
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
        return DeviceView.from(devices.register(actor.tenantId(), actor.recipientId(),
                request.token(), request.platform(), request.appInstance()));
    }

    public List<DeviceView> activeDevices() {
        var actor = requireActor();
        return devices.activeDevices(actor.tenantId(), actor.recipientId()).stream()
                .map(DeviceView::from).toList();
    }

    public Optional<DeviceView> unregisterDevice(UUID deviceId) {
        Objects.requireNonNull(deviceId, "deviceId is required");
        var actor = requireActor();
        return devices.unregister(actor.tenantId(), actor.recipientId(), deviceId).map(DeviceView::from);
    }

    private NotificationRequestContext.Actor requireActor() {
        return requestContext.currentActor()
                .orElseThrow(() -> new UnauthenticatedNotificationRequestException());
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

    /**
     * Safe device response; provider credentials are never exposed to clients.
     */
    public record DeviceView(
            UUID id,
            PushDevice.Platform platform,
            String appInstance,
            Instant createdAt,
            Instant updatedAt) {
        static DeviceView from(PushDevice device) {
            return new DeviceView(device.id(), device.platform(), device.appInstance(),
                    device.createdAt(), device.updatedAt());
        }
    }

    /** Host REST adapters should map this to HTTP 401. */
    public static final class UnauthenticatedNotificationRequestException extends RuntimeException {
        public UnauthenticatedNotificationRequestException() {
            super("An authenticated notification actor is required");
        }
    }
}