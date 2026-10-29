package tech.kayys.numou.notification.persistence.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.notification.application.port.PushTokenProtector;
import tech.kayys.numou.notification.domain.Notification;
import tech.kayys.numou.notification.domain.NotificationDeliveryJob;
import tech.kayys.numou.notification.domain.PushDevice;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcNotificationDeliveryJobRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcNotificationDeliveryJobRepository repository;
    private JdbcNotificationRepository notifications;
    private JdbcPushDeviceRepository devices;
    private final Instant now = Instant.parse("2026-10-29T00:00:00Z");

    @BeforeEach
    void setUp() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        executeMigrations(dataSource);
        repository = new JdbcNotificationDeliveryJobRepository(dataSource);
        notifications = new JdbcNotificationRepository(dataSource);
        devices = new JdbcPushDeviceRepository(dataSource, protector());
    }

    @Test
    void enqueueIsIdempotentAndRejectsKeyReuseForDifferentTarget() {
        Fixture fixture = fixture();
        NotificationDeliveryJob first = job(fixture, "notification-device-1");
        NotificationDeliveryJob stored = repository.enqueue(first);
        NotificationDeliveryJob duplicate = repository.enqueue(job(fixture, "notification-device-1"));
        assertEquals(stored.id(), duplicate.id());

        NotificationDeliveryJob conflicting = NotificationDeliveryJob.pending(
                fixture.tenant(), fixture.recipient(), fixture.notification().id(),
                UUID.randomUUID(), "FCM", "notification-device-1", now);
        assertThrows(IllegalStateException.class, () -> repository.enqueue(conflicting));
    }

    @Test
    void claimsAreExclusiveAndDeliveryRequiresLeaseToken() {
        Fixture fixture = fixture();
        NotificationDeliveryJob queued = repository.enqueue(job(fixture, "claim-once"));
        UUID lease = UUID.randomUUID();
        List<NotificationDeliveryJob> claimed = repository.claimDue(now, now.plusSeconds(30), 10, lease);
        assertEquals(1, claimed.size());
        assertEquals(queued.id(), claimed.getFirst().id());
        assertEquals(1, claimed.getFirst().attemptCount());
        assertTrue(repository.claimDue(now, now.plusSeconds(30), 10, UUID.randomUUID()).isEmpty());

        assertTrue(repository.markDelivered(fixture.tenant(), queued.id(), UUID.randomUUID(), now.plusSeconds(1)).isEmpty());
        NotificationDeliveryJob delivered = repository.markDelivered(
                fixture.tenant(), queued.id(), lease, now.plusSeconds(1)).orElseThrow();
        assertEquals(NotificationDeliveryJob.Status.DELIVERED, delivered.status());
    }

    @Test
    void expiredLeaseCanBeReclaimedAndStaleWorkerCannotComplete() {
        Fixture fixture = fixture();
        NotificationDeliveryJob queued = repository.enqueue(job(fixture, "reclaim"));
        UUID oldLease = UUID.randomUUID();
        repository.claimDue(now, now.plusSeconds(5), 10, oldLease);

        UUID newLease = UUID.randomUUID();
        NotificationDeliveryJob reclaimed = repository.claimDue(
                now.plusSeconds(6), now.plusSeconds(20), 10, newLease).getFirst();
        assertEquals(2, reclaimed.attemptCount());
        assertTrue(repository.markDelivered(fixture.tenant(), queued.id(), oldLease, now.plusSeconds(7)).isEmpty());
        assertTrue(repository.scheduleRetry(fixture.tenant(), queued.id(), newLease,
                now.plusSeconds(60), "provider_timeout").isPresent());
    }

    @Test
    void retryWaitIsNotClaimedBeforeDueTimeAndCanBeDeadLettered() {
        Fixture fixture = fixture();
        NotificationDeliveryJob queued = repository.enqueue(job(fixture, "retry"));
        UUID firstLease = UUID.randomUUID();
        repository.claimDue(now, now.plusSeconds(10), 10, firstLease);
        repository.scheduleRetry(fixture.tenant(), queued.id(), firstLease, now.plusSeconds(60), "timeout");

        assertTrue(repository.claimDue(now.plusSeconds(59), now.plusSeconds(90), 10, UUID.randomUUID()).isEmpty());
        UUID secondLease = UUID.randomUUID();
        repository.claimDue(now.plusSeconds(60), now.plusSeconds(90), 10, secondLease);
        assertEquals(NotificationDeliveryJob.Status.DEAD, repository.markDead(
                fixture.tenant(), queued.id(), secondLease, "invalid_device_token").orElseThrow().status());
    }

    @Test
    void tenantAndRecipientMustMatchReferencedInboxAndDevice() throws Exception {
        Fixture fixture = fixture();
        NotificationDeliveryJob invalid = NotificationDeliveryJob.pending(
                UUID.randomUUID(), fixture.recipient(), fixture.notification().id(),
                fixture.device().id(), "FCM", "cross-tenant", now);
        assertThrows(IllegalStateException.class, () -> repository.enqueue(invalid));
    }

    private Fixture fixture() {
        UUID tenant = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        Notification notification = notifications.createIfAbsent(Notification.create(
                tenant, recipient, "test.event", "Delivery test", "Test body",
                "test:source", UUID.randomUUID().toString(), now));
        PushDevice device = devices.registerOrRefresh(PushDevice.register(
                tenant, recipient, "provider-token-" + UUID.randomUUID(),
                PushDevice.Platform.ANDROID, "test-app", now));
        return new Fixture(tenant, recipient, notification, device);
    }

    private NotificationDeliveryJob job(Fixture f, String key) {
        return NotificationDeliveryJob.pending(f.tenant(), f.recipient(), f.notification().id(),
                f.device().id(), "FCM", key, now);
    }

    private PushTokenProtector protector() {
        SecretKey key = new SecretKeySpec(new byte[32], "AES");
        PushTokenKeyProvider keys = new PushTokenKeyProvider() {
            public KeyMaterial activeKey() { return new KeyMaterial("test-key-v1", key); }
            public Optional<SecretKey> keyFor(String keyId) {
                return "test-key-v1".equals(keyId) ? Optional.of(key) : Optional.empty();
            }
        };
        return new AesGcmPushTokenProtector(keys);
    }

    private static void executeMigrations(DataSource dataSource) throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS notification_delivery_job CASCADE");
            statement.execute("DROP TABLE IF EXISTS notification_push_device CASCADE");
            statement.execute("DROP TABLE IF EXISTS notification_inbox CASCADE");
        }
        for (String migration : List.of(
                "V1__notification_inbox.sql",
                "V2__notification_push_device.sql",
                "V3__notification_delivery_jobs.sql",
                "V4__notification_push_token_encryption.sql")) {
            String sql;
            try (var stream = JdbcNotificationDeliveryJobRepositoryTest.class
                    .getResourceAsStream("/db/migration/" + migration)) {
                if (stream == null) throw new IllegalStateException("Migration missing: " + migration);
                sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                for (String command : sql.split(";")) {
                    if (!command.isBlank()) statement.execute(command);
                }
            }
        }
    }

    private record Fixture(UUID tenant, UUID recipient, Notification notification, PushDevice device) {}
}
