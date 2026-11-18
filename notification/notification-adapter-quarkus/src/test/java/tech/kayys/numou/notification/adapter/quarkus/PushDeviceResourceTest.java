package tech.kayys.numou.notification.adapter.quarkus;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.notification.application.AuthenticatedNotificationApi;
import tech.kayys.numou.notification.application.NotificationService;
import tech.kayys.numou.notification.application.PushDeviceService;
import tech.kayys.numou.notification.application.port.NotificationRepository;
import tech.kayys.numou.notification.application.port.NotificationRequestContext;
import tech.kayys.numou.notification.application.port.PushDeviceRepository;
import tech.kayys.numou.notification.domain.Notification;
import tech.kayys.numou.notification.domain.PushDevice;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PushDeviceResourceTest {
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RECIPIENT = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void registrationAndListingReturnOnlyDeviceMetadata() {
        var repository = new InMemoryDevices();
        var resource = resource(repository);

        Response registered = resource.register(new PushDeviceResource.RegisterDeviceBody(
                "sensitive-provider-token", PushDevice.Platform.WEB, "browser-1"));
        assertEquals(200, registered.getStatus());
        var created = (PushDeviceResource.DeviceResponse) registered.getEntity();
        assertEquals(PushDevice.Platform.WEB, created.platform());
        assertFalse(created.toString().contains("sensitive-provider-token"));

        var listed = resource.list();
        assertEquals(1, listed.size());
        assertEquals(created.id(), listed.getFirst().id());
        assertFalse(listed.getFirst().toString().contains("sensitive-provider-token"));
    }

    @Test
    void revokeIsScopedAndReturnsNotFoundForUnknownDevice() {
        var repository = new InMemoryDevices();
        var resource = resource(repository);
        var created = (PushDeviceResource.DeviceResponse) resource.register(
                new PushDeviceResource.RegisterDeviceBody("token", PushDevice.Platform.ANDROID, "app"))
                .getEntity();

        assertEquals(200, resource.unregister(created.id()).getStatus());
        assertEquals(404, resource.unregister(UUID.randomUUID()).getStatus());
    }

    private static PushDeviceResource resource(InMemoryDevices devices) {
        var actor = new NotificationRequestContext.Actor(TENANT, RECIPIENT);
        var api = new AuthenticatedNotificationApi(
                new NotificationService(new NoopNotifications(), CLOCK),
                new PushDeviceService(devices, CLOCK),
                () -> Optional.of(actor));
        return new PushDeviceResource(api);
    }

    private static final class NoopNotifications implements NotificationRepository {
        @Override public Notification createIfAbsent(Notification notification) { return notification; }
        @Override public List<Notification> findInbox(UUID tenantId, UUID recipientId, int limit, Instant before) {
            return List.of();
        }
        @Override public Optional<Notification> findById(UUID tenantId, UUID recipientId, UUID id) {
            return Optional.empty();
        }
        @Override public Optional<Notification> markRead(UUID tenantId, UUID recipientId, UUID id, Instant at) {
            return Optional.empty();
        }
    }

    private static final class InMemoryDevices implements PushDeviceRepository {
        private PushDevice device;

        @Override public PushDevice registerOrRefresh(PushDevice candidate) {
            device = device == null ? candidate : device.refresh(candidate.token(), candidate.platform(),
                    candidate.appInstance(), candidate.updatedAt());
            return device;
        }
        @Override public List<PushDevice> findActiveByRecipient(UUID tenantId, UUID recipientId) {
            return device != null && device.isActive() && device.tenantId().equals(tenantId)
                    && device.recipientId().equals(recipientId) ? List.of(device) : List.of();
        }
        @Override public Optional<PushDevice> disable(UUID tenantId, UUID recipientId, UUID deviceId, Instant at) {
            if (device == null || !device.id().equals(deviceId) || !device.tenantId().equals(tenantId)
                    || !device.recipientId().equals(recipientId)) return Optional.empty();
            device = device.disable(at);
            return Optional.of(device);
        }
    }
}
