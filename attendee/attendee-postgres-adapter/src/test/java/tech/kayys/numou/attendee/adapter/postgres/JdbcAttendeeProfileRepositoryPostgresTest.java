package tech.kayys.numou.attendee.adapter.postgres;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.attendee.application.error.AttendeeProfileAlreadyExistsException;
import tech.kayys.numou.attendee.application.error.AttendeeProfileOptimisticLockException;
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

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcAttendeeProfileRepositoryPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private DataSource dataSource;
    private JdbcAttendeeProfileRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = dataSource();
        createPartyFixture();
        migrateAttendee();
        repository = new JdbcAttendeeProfileRepository(dataSource);
    }

    @Test
    void persistsAndRehydratesTenantScopedProfile() {
        UUID partyId = createParty();
        TenantId tenantId = TenantId.generate();
        AttendeeProfile original = profile(tenantId, partyId, "EVENT_GUEST");

        assertEquals(original, repository.save(original).await().indefinitely());
        AttendeeProfile loaded = repository.findByTenantAndId(tenantId, original.id())
                .await().indefinitely().orElseThrow();

        assertEquals(original, loaded);
        assertEquals(AttendeeProfileStatus.PENDING, loaded.status());
        assertEquals(0, loaded.revision());
        assertTrue(repository.findByTenantAndId(TenantId.generate(), original.id())
                .await().indefinitely().isEmpty());
    }

    @Test
    void enforcesTenantPartyKindUniquenessButAllowsAnotherTenant() {
        UUID partyId = createParty();
        TenantId tenantA = TenantId.generate();
        TenantId tenantB = TenantId.generate();

        repository.save(profile(tenantA, partyId, "EVENT_GUEST")).await().indefinitely();
        assertThrows(AttendeeProfileAlreadyExistsException.class,
                () -> repository.save(profile(tenantA, partyId, "EVENT_GUEST"))
                        .await().indefinitely());

        repository.save(profile(tenantB, partyId, "EVENT_GUEST")).await().indefinitely();
        assertTrue(repository.findByTenantPartyAndKind(tenantA, partyId, "EVENT_GUEST")
                .await().indefinitely().isPresent());
        assertTrue(repository.findByTenantPartyAndKind(tenantB, partyId, "EVENT_GUEST")
                .await().indefinitely().isPresent());
    }

    @Test
    void rejectsStaleRevisionAndPersistsValidTransition() {
        UUID partyId = createParty();
        TenantId tenantId = TenantId.generate();
        AttendeeProfile original = profile(tenantId, partyId, "EVENT_GUEST");
        repository.save(original).await().indefinitely();

        AttendeeProfile first = repository.findByTenantAndId(tenantId, original.id())
                .await().indefinitely().orElseThrow();
        AttendeeProfile stale = repository.findByTenantAndId(tenantId, original.id())
                .await().indefinitely().orElseThrow();

        AttendeeProfile activated = first.activate(Instant.parse("2026-11-06T11:00:00Z"));
        repository.save(activated).await().indefinitely();
        assertThrows(AttendeeProfileOptimisticLockException.class,
                () -> repository.save(stale.activate(Instant.parse("2026-11-06T11:01:00Z")))
                        .await().indefinitely());

        AttendeeProfile loaded = repository.findByTenantAndId(tenantId, original.id())
                .await().indefinitely().orElseThrow();
        assertEquals(AttendeeProfileStatus.ACTIVE, loaded.status());
        assertEquals(1, loaded.revision());
    }

    @Test
    void refusesToAttachUnknownCanonicalParty() {
        AttendeeProfile orphan = profile(TenantId.generate(), UUID.randomUUID(), "EVENT_GUEST");
        assertThrows(IllegalArgumentException.class,
                () -> repository.save(orphan).await().indefinitely());
    }

    private AttendeeProfile profile(TenantId tenantId, UUID partyId, String kind) {
        Instant now = Instant.parse("2026-11-06T10:00:00Z");
        return AttendeeProfile.create(UUID.randomUUID(), tenantId, PartyId.of(partyId),
                kind, "Attendee", null, "id-ID", now);
    }

    private UUID createParty() {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO ecosystem_party (party_id, kind, status, created_at, updated_at, version) "
                             + "VALUES (?, 'PERSON', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)")) {
            statement.setObject(1, id);
            statement.executeUpdate();
            return id;
        } catch (Exception e) {
            throw new IllegalStateException("Could not create canonical Party fixture", e);
        }
    }

    private DataSource dataSource() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
    }

    private void createPartyFixture() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS attendee_profile CASCADE");
            statement.execute("DROP TABLE IF EXISTS ecosystem_party CASCADE");
            statement.execute("""
                    CREATE TABLE ecosystem_party (
                        party_id UUID PRIMARY KEY,
                        kind VARCHAR(24) NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL,
                        updated_at TIMESTAMPTZ NOT NULL,
                        version INTEGER NOT NULL DEFAULT 0
                    )
                    """);
        }
    }

    private void migrateAttendee() throws Exception {
        String resource = "db/migration/V1__attendee_profiles.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "Missing migration resource " + resource);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }
}
