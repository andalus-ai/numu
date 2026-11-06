package tech.kayys.numou.attendee.persistence.jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.attendee.application.port.AttendeeProfileAlreadyExistsException;
import tech.kayys.numou.attendee.application.port.AttendeeProfileOptimisticLockException;
import tech.kayys.numou.attendee.domain.AttendeeProfile;
import tech.kayys.numou.attendee.domain.AttendeeProfileStatus;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcAttendeeProfileRepositoryPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void persistsAndRehydratesTenantScopedProfile() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        var repository = new JdbcAttendeeProfileRepository(dataSource);
        var tenantId = TenantId.generate();
        var partyId = PartyId.generate();
        var profile = AttendeeProfile.create(UUID.randomUUID(), tenantId, partyId,
                "EVENT_GUEST", "Guest", "Preferred", "id-ID",
                Instant.parse("2026-11-06T00:00:00Z"));

        repository.save(profile).await().indefinitely();
        var loaded = repository.findByTenantAndId(tenantId, profile.id())
                .await().indefinitely().orElseThrow();

        assertEquals(profile.id(), loaded.id());
        assertEquals(tenantId, loaded.tenantId());
        assertEquals(partyId, loaded.partyId());
        assertEquals("EVENT_GUEST", loaded.profileKind());
        assertEquals("Guest", loaded.displayName());
        assertEquals("Preferred", loaded.preferredName());
        assertEquals("id-ID", loaded.preferredLanguage());
        assertEquals(AttendeeProfileStatus.PENDING, loaded.status());
        assertEquals(0, loaded.revision());
    }

    @Test
    void doesNotExposeProfileAcrossTenantsAndAllowsIndependentTenantProfiles() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        var repository = new JdbcAttendeeProfileRepository(dataSource);
        var tenantA = TenantId.generate();
        var tenantB = TenantId.generate();
        var partyId = PartyId.generate();
        var profileA = AttendeeProfile.create(UUID.randomUUID(), tenantA, partyId,
                "EVENT_GUEST", "Guest A", null, null, Instant.parse("2026-11-06T00:00:00Z"));
        var profileB = AttendeeProfile.create(UUID.randomUUID(), tenantB, partyId,
                "EVENT_GUEST", "Guest B", null, null, Instant.parse("2026-11-06T00:00:00Z"));

        repository.save(profileA).await().indefinitely();
        repository.save(profileB).await().indefinitely();

        assertTrue(repository.findByTenantAndId(tenantB, profileA.id())
                .await().indefinitely().isEmpty());
        assertEquals(profileA.id(), repository.findByTenantPartyAndKind(
                tenantA, partyId.value(), "EVENT_GUEST").await().indefinitely().orElseThrow().id());
        assertEquals(profileB.id(), repository.findByTenantPartyAndKind(
                tenantB, partyId.value(), "EVENT_GUEST").await().indefinitely().orElseThrow().id());
    }

    @Test
    void enforcesTenantPartyKindUniquenessAtDatabaseBoundary() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        var repository = new JdbcAttendeeProfileRepository(dataSource);
        var tenantId = TenantId.generate();
        var partyId = PartyId.generate();
        var createdAt = Instant.parse("2026-11-06T00:00:00Z");
        var first = AttendeeProfile.create(UUID.randomUUID(), tenantId, partyId,
                "EVENT_GUEST", "First", null, null, createdAt);
        var duplicate = AttendeeProfile.create(UUID.randomUUID(), tenantId, partyId,
                "EVENT_GUEST", "Duplicate", null, null, createdAt);

        repository.save(first).await().indefinitely();
        assertThrows(AttendeeProfileAlreadyExistsException.class,
                () -> repository.save(duplicate).await().indefinitely());
    }

    @Test
    void rejectsStaleRevisionAndPersistsValidLifecycleUpdate() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        var repository = new JdbcAttendeeProfileRepository(dataSource);
        var tenantId = TenantId.generate();
        var profile = AttendeeProfile.create(UUID.randomUUID(), tenantId, PartyId.generate(),
                "EVENT_GUEST", "Guest", null, null, Instant.parse("2026-11-06T00:00:00Z"));
        repository.save(profile).await().indefinitely();

        var firstRead = repository.findByTenantAndId(tenantId, profile.id())
                .await().indefinitely().orElseThrow();
        var staleRead = repository.findByTenantAndId(tenantId, profile.id())
                .await().indefinitely().orElseThrow();
        var updated = firstRead.activate(Instant.parse("2026-11-06T00:01:00Z"));
        repository.save(updated).await().indefinitely();

        assertThrows(AttendeeProfileOptimisticLockException.class,
                () -> repository.save(staleRead.activate(Instant.parse("2026-11-06T00:02:00Z")))
                        .await().indefinitely());

        var reloaded = repository.findByTenantAndId(tenantId, profile.id())
                .await().indefinitely().orElseThrow();
        assertEquals(AttendeeProfileStatus.ACTIVE, reloaded.status());
        assertEquals(1, reloaded.revision());
    }


    @Test
    void concurrentCreatesAreSerializedByDatabaseUniqueness() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        var repository = new JdbcAttendeeProfileRepository(dataSource);
        var tenantId = TenantId.generate();
        var partyId = PartyId.generate();
        var createdAt = Instant.parse("2026-11-06T00:00:00Z");
        var first = AttendeeProfile.create(UUID.randomUUID(), tenantId, partyId,
                "EVENT_GUEST", "First", null, null, createdAt);
        var second = AttendeeProfile.create(UUID.randomUUID(), tenantId, partyId,
                "EVENT_GUEST", "Second", null, null, createdAt);
        var gate = new CountDownLatch(1);

        var firstWrite = CompletableFuture.supplyAsync(() -> {
            awaitGate(gate);
            return saveOrConflict(repository, first);
        });
        var secondWrite = CompletableFuture.supplyAsync(() -> {
            awaitGate(gate);
            return saveOrConflict(repository, second);
        });
        gate.countDown();

        int committed = (firstWrite.join() ? 1 : 0) + (secondWrite.join() ? 1 : 0);
        assertEquals(1, committed);
    }

    private static void awaitGate(CountDownLatch gate) {
        try {
            gate.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting concurrent repository writes", interrupted);
        }
    }

    private static boolean saveOrConflict(
            JdbcAttendeeProfileRepository repository, AttendeeProfile profile) {
        try {
            repository.save(profile).await().indefinitely();
            return true;
        } catch (AttendeeProfileAlreadyExistsException expected) {
            return false;
        }
    }

    private DataSource dataSource() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
    }

    private void migrate(DataSource dataSource) throws Exception {
        String resource = "db/migration/attendee/V1__attendee_profiles.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "Missing migration resource " + resource);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS attendee_profile CASCADE");
                statement.execute(sql);
            }
        }
    }
}
