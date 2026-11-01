package tech.kayys.numou.notification.adapter.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Test-only CDI composition; production hosts must supply their own adapters. */
@ApplicationScoped
public class NotificationHttpTestBeans {
    static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID PRINCIPAL = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    static final UUID RECIPIENT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private final InMemoryDevices devices = new InMemoryDevices();

    @Produces
    VerifiedNotificationPrincipalProvider principalProvider() {
        return () -> Optional.of(new VerifiedNotificationPrincipalProvider.VerifiedPrincipal(TENANT, PRINCIPAL));
    }

    @Produces
    NotificationRecipientResolver recipientResolver() {
        return (tenantId, principalId) -> TENANT.equals(tenantId) && PRINCIPAL.equals(principalId)
                ? Optional.of(RECIPIENT) : Optional.empty();
    }

    @Produces
    AuthenticatedNotificationApi authenticatedNotificationApi(NotificationRequestContext context) {
        return new AuthenticatedNotificationApi(
                new NotificationService(new NoopNotifications(), CLOCK),
                new PushDeviceService(devices, CLOCK),
                context);
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
        private final ConcurrentMap<UUID, PushDevice> devices = new ConcurrentHashMap<>();

        @Override
        public PushDevice registerOrRefresh(PushDevice candidate) {
            return devices.compute(candidate.id(), (id, existing) -> existing == null
                    ? candidate
                    : existing.refresh(candidate.token(), candidate.platform(),
                            candidate.appInstance(), candidate.updatedAt()));
        }

        @Override
        public List<PushDevice> findActiveByRecipient(UUID tenantId, UUID recipientId) {
            return devices.values().stream()
                    .filter(PushDevice::isActive)
                    .filter(device -> tenantId.equals(device.tenantId()))
                    .filter(device -> recipientId.equals(device.recipientId()))
                    .toList();
        }

        @Override
        public Optional<PushDevice> disable(
                UUID tenantId, UUID recipientId, UUID deviceId, Instant disabledAt) {
            return Optional.ofNullable(devices.computeIfPresent(deviceId, (id, device) -> {
                if (!tenantId.equals(device.tenantId()) || !recipientId.equals(device.recipientId())) {
                    return device;
                }
                return device.disable(disabledAt);
            })).filter(device -> tenantId.equals(device.tenantId())
                    && recipientId.equals(device.recipientId()));
        }
    }
}
