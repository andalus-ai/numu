package tech.kayys.numou.notification.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.notification.application.port.NotificationRepository;
import tech.kayys.numou.notification.application.port.NotificationRequestContext;
import tech.kayys.numou.notification.application.port.PushDeviceRepository;
import tech.kayys.numou.notification.domain.Notification;
import tech.kayys.numou.notification.domain.PushDevice;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticatedNotificationApiTest {
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RECIPIENT = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void scopesInboxAndDeviceRegistrationToVerifiedActor() {
        var notificationRepo = new RecordingNotificationRepository();
        var deviceRepo = new RecordingPushDeviceRepository();
        var api = api(Optional.of(new NotificationRequestContext.Actor(TENANT, RECIPIENT)),
                notificationRepo, deviceRepo);

        api.inbox(25, null);
        var device = api.registerDevice(new AuthenticatedNotificationApi.RegisterDeviceRequest(
                "sensitive-provider-token", PushDevice.Platform.ANDROID, "app-1"));

        assertEquals(TENANT, notificationRepo.tenantId);
        assertEquals(RECIPIENT, notificationRepo.recipientId);
        assertEquals(TENANT, deviceRepo.registered.tenantId());
        assertEquals(RECIPIENT, deviceRepo.registered.recipientId());
        assertEquals(deviceRepo.registered.id(), device.id());
        assertFalse(device.toString().contains("sensitive-provider-token"));
        assertFalse(deviceRepo.registered.toString().contains("sensitive-provider-token"));
    }

    @Test
    void rejectsRequestsWithoutAuthenticatedActorBeforeCallingRepositories() {
        var notificationRepo = new RecordingNotificationRepository();
        var deviceRepo = new RecordingPushDeviceRepository();
        var api = api(Optional.empty(), notificationRepo, deviceRepo);

        assertThrows(AuthenticatedNotificationApi.UnauthenticatedNotificationRequestException.class,
                () -> api.inbox(10, null));
        assertThrows(AuthenticatedNotificationApi.UnauthenticatedNotificationRequestException.class,
                () -> api.registerDevice(new AuthenticatedNotificationApi.RegisterDeviceRequest(
                        "token", PushDevice.Platform.WEB, "browser")));
        assertThrows(AuthenticatedNotificationApi.UnauthenticatedNotificationRequestException.class,
                api::activeDevices);

        assertNull(notificationRepo.tenantId);
        assertNull(deviceRepo.registered);
        assertNull(deviceRepo.tenantId);
    }

    @Test
    void managementOperationsUseOnlySafeSummariesAndAuthenticatedScope() {
        var deviceRepo = new RecordingPushDeviceRepository();
        var api = api(Optional.of(new NotificationRequestContext.Actor(TENANT, RECIPIENT)),
                new RecordingNotificationRepository(), deviceRepo);

        var registered = api.registerDevice(new AuthenticatedNotificationApi.RegisterDeviceRequest(
                "private-web-push-subscription", PushDevice.Platform.WEB, "browser-1"));
        var listed = api.activeDevices();

        assertEquals(1, listed.size());
        assertEquals(registered.id(), listed.getFirst().id());
        assertTrue(listed.getFirst().active());
        assertFalse(listed.getFirst().toString().contains("private-web-push-subscription"));
        assertEquals(TENANT, deviceRepo.tenantId);
        assertEquals(RECIPIENT, deviceRepo.recipientId);

        var removed = api.unregisterDevice(registered.id()).orElseThrow();
        assertFalse(removed.active());
        assertEquals(registered.id(), removed.id());
        assertEquals(TENANT, deviceRepo.tenantId);
        assertEquals(RECIPIENT, deviceRepo.recipientId);
        assertEquals(registered.id(), deviceRepo.disabledDeviceId);
    }

    @Test
    void publicDeviceViewNeverContainsProviderToken() {
        var deviceRepo = new RecordingPushDeviceRepository();
        var api = api(Optional.of(new NotificationRequestContext.Actor(TENANT, RECIPIENT)),
                new RecordingNotificationRepository(), deviceRepo);

        var view = api.registerDevice(new AuthenticatedNotificationApi.RegisterDeviceRequest(
                "private-token-value", PushDevice.Platform.IOS, "ios-app"));

        assertEquals(PushDevice.Platform.IOS, view.platform());
        assertFalse(view.toString().contains("private-token-value"));
    }

    private static AuthenticatedNotificationApi api(
            Optional<NotificationRequestContext.Actor> actor,
            RecordingNotificationRepository notificationRepo,
            RecordingPushDeviceRepository deviceRepo) {
        return new AuthenticatedNotificationApi(
                new NotificationService(notificationRepo, CLOCK),
                new PushDeviceService(deviceRepo, CLOCK),
                () -> actor);
    }

    private static final class RecordingNotificationRepository implements NotificationRepository {
        UUID tenantId;
        UUID recipientId;

        @Override
        public Notification createIfAbsent(Notification notification) {
            return notification;
        }

        @Override
        public List<Notification> findInbox(UUID tenantId, UUID recipientId, int limit, Instant beforeCreatedAt) {
            this.tenantId = tenantId;
            this.recipientId = recipientId;
            return List.of();
        }

        @Override
        public Optional<Notification> findById(UUID tenantId, UUID recipientId, UUID notificationId) {
            this.tenantId = tenantId;
            this.recipientId = recipientId;
            return Optional.empty();
        }

        @Override
        public Optional<Notification> markRead(UUID tenantId, UUID recipientId, UUID notificationId, Instant readAt) {
            this.tenantId = tenantId;
            this.recipientId = recipientId;
            return Optional.empty();
        }
    }

    private static final class RecordingPushDeviceRepository implements PushDeviceRepository {
        PushDevice registered;
        UUID tenantId;
        UUID recipientId;
        UUID disabledDeviceId;

        @Override
        public PushDevice registerOrRefresh(PushDevice device) {
            registered = device;
            return device;
        }

        @Override
        public List<PushDevice> findActiveByRecipient(UUID tenantId, UUID recipientId) {
            this.tenantId = tenantId;
            this.recipientId = recipientId;
            return registered == null ? List.of() : List.of(registered);
        }

        @Override
        public Optional<PushDevice> disable(UUID tenantId, UUID recipientId, UUID deviceId, Instant disabledAt) {
            this.tenantId = tenantId;
            this.recipientId = recipientId;
            disabledDeviceId = deviceId;
            if (registered == null || !registered.id().equals(deviceId)) return Optional.empty();
            return Optional.of(registered.disable(disabledAt));
        }

        @Override
        public Optional<PushDeviceSummary> disableSummary(
                UUID tenantId, UUID recipientId, UUID deviceId, Instant disabledAt) {
            this.tenantId = tenantId;
            this.recipientId = recipientId;
            disabledDeviceId = deviceId;
            if (registered == null || !registered.id().equals(deviceId)) return Optional.empty();
            return Optional.of(PushDeviceSummary.from(registered.disable(disabledAt)));
        }
    }
}
