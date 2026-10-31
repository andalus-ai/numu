package tech.kayys.numou.notification.adapter.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
class NotificationHttpTestComposition {
    static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID PRINCIPAL = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    static final UUID RECIPIENT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Produces
    @Singleton
    VerifiedNotificationPrincipalProvider verifiedPrincipal() {
        return () -> Optional.of(new VerifiedNotificationPrincipalProvider.VerifiedPrincipal(TENANT, PRINCIPAL));
    }

    @Produces
    @Singleton
    NotificationRecipientResolver recipientResolver() {
        return (tenantId, principalId) -> TENANT.equals(tenantId) && PRINCIPAL.equals(principalId)
                ? Optional.of(RECIPIENT) : Optional.empty();
    }

    @Produces
    @Singleton
    AuthenticatedNotificationApi authenticatedApi(
            NotificationHttpTestStore store,
            NotificationRequestContext requestContext) {
        Clock clock = Clock.systemUTC();
        return new AuthenticatedNotificationApi(
                new NotificationService(store, clock),
                new PushDeviceService(store, clock),
                requestContext);
    }
}

@ApplicationScoped
class NotificationHttpTestStore implements NotificationRepository, PushDeviceRepository {
    private final ConcurrentHashMap<UUID, PushDevice> devices = new ConcurrentHashMap<>();

    void clear() {
        devices.clear();
    }

    @Override
    public Notification createIfAbsent(Notification notification) {
        return notification;
    }

    @Override
    public List<Notification> findInbox(UUID tenantId, UUID recipientId, int limit, Instant beforeCreatedAt) {
        return List.of();
    }

    @Override
    public Optional<Notification> findById(UUID tenantId, UUID recipientId, UUID notificationId) {
        return Optional.empty();
    }

    @Override
    public Optional<Notification> markRead(UUID tenantId, UUID recipientId, UUID notificationId, Instant readAt) {
        return Optional.empty();
    }

    @Override
    public PushDevice registerOrRefresh(PushDevice candidate) {
        return devices.compute(candidate.id(), (id, current) -> current == null ? candidate
                : current.refresh(candidate.token(), candidate.platform(), candidate.appInstance(), candidate.updatedAt()));
    }

    @Override
    public List<PushDevice> findActiveByRecipient(UUID tenantId, UUID recipientId) {
        return devices.values().stream()
                .filter(PushDevice::isActive)
                .filter(device -> device.tenantId().equals(tenantId) && device.recipientId().equals(recipientId))
                .toList();
    }

    @Override
    public Optional<PushDevice> disable(UUID tenantId, UUID recipientId, UUID deviceId, Instant disabledAt) {
        return devices.computeIfPresent(deviceId, (id, current) ->
                current.tenantId().equals(tenantId) && current.recipientId().equals(recipientId)
                        ? current.disable(disabledAt) : current) == null
                ? Optional.empty()
                : Optional.ofNullable(devices.get(deviceId))
                        .filter(device -> device.tenantId().equals(tenantId)
                                && device.recipientId().equals(recipientId));
    }
}
