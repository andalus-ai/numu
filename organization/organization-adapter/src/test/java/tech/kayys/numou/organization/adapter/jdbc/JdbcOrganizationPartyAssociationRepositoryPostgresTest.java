package tech.kayys.numou.organization.adapter.jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationPartyAssociation;
import tech.kayys.numou.organization.domain.OrganizationPartyAssociationStatus;
import tech.kayys.numou.organization.spi.OrganizationPartyAssociationRepository;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcOrganizationPartyAssociationRepositoryPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void persistsLifecycleAndAtomicHistoryAndSupportsExactReplay() throws Exception {
        var repository = repository();
        var proposed = OrganizationPartyAssociation.propose(
                UUID.randomUUID(), OrganizationId.generate(), PartyId.generate(),
                T0, "operator-1", "registry:entry-9");
        repository.save(proposed).toCompletableFuture().join();

        var active = proposed.activate(T0.plusSeconds(10), "verifier-1");
        repository.save(active).toCompletableFuture().join();
        repository.save(active).toCompletableFuture().join();

        var loaded = repository.findById(active.id()).toCompletableFuture().join().orElseThrow();
        assertEquals(active, loaded);
        assertEquals(OrganizationPartyAssociationStatus.ACTIVE, loaded.status());
        var history = repository.findHistory(active.id()).toCompletableFuture().join();
        assertEquals(2, history.size());
        assertEquals("PROPOSED", history.get(0).eventType());
        assertEquals("PENDING_VERIFICATION_TO_ACTIVE", history.get(1).eventType());
    }

    @Test
    void associationHistoryRejectsUpdateDeleteAndTruncate() throws Exception {
        var repository = repository();
        var proposed = OrganizationPartyAssociation.propose(
                UUID.randomUUID(), OrganizationId.generate(), PartyId.generate(),
                T0, "operator-1", null);
        repository.save(proposed).toCompletableFuture().join();

        try (Connection connection = dataSource().getConnection();
             Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "UPDATE organization_party_association_history SET reason = 'tampered' "
                            + "WHERE association_id = '" + proposed.id() + "'"));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "DELETE FROM organization_party_association_history "
                            + "WHERE association_id = '" + proposed.id() + "'"));
            assertThrows(SQLException.class,
                    () -> statement.execute("TRUNCATE organization_party_association_history"));
        }

        assertEquals(1, repository.findHistory(proposed.id()).toCompletableFuture().join().size());
    }

    @Test
    void databaseEnforcesOneCurrentAssociationPerOrganizationAndParty() throws Exception {
        var repository = repository();
        OrganizationId organizationId = OrganizationId.generate();
        PartyId partyId = PartyId.generate();
        var first = OrganizationPartyAssociation.propose(
                UUID.randomUUID(), organizationId, partyId, T0, "operator-1", null);
        repository.save(first).toCompletableFuture().join();

        var sameOrganization = OrganizationPartyAssociation.propose(
                UUID.randomUUID(), organizationId, PartyId.generate(), T0, "operator-2", null);
        assertThrows(RuntimeException.class,
                () -> repository.save(sameOrganization).toCompletableFuture().join());

        var sameParty = OrganizationPartyAssociation.propose(
                UUID.randomUUID(), OrganizationId.generate(), partyId, T0, "operator-3", null);
        assertThrows(RuntimeException.class,
                () -> repository.save(sameParty).toCompletableFuture().join());
    }

    @Test
    void staleTransitionCannotOverwriteNewerVersion() throws Exception {
        var repository = repository();
        var proposed = OrganizationPartyAssociation.propose(
                UUID.randomUUID(), OrganizationId.generate(), PartyId.generate(), T0, "operator-1", null);
        repository.save(proposed).toCompletableFuture().join();

        var active = proposed.activate(T0.plusSeconds(1), "verifier-1");
        repository.save(active).toCompletableFuture().join();
        var stale = proposed.activate(T0.plusSeconds(2), "verifier-2");
        assertThrows(RuntimeException.class, () -> repository.save(stale).toCompletableFuture().join());
    }

    @Test
    void writesTenantScopedLifecycleEventToPlatformOutbox() throws Exception {
        var repository = repository();
        UUID tenantId = UUID.randomUUID();
        var proposed = OrganizationPartyAssociation.propose(
                UUID.randomUUID(), OrganizationId.generate(), PartyId.generate(),
                T0, "operator-1", null);
        repository.save(proposed, tenantId).toCompletableFuture().join();
        repository.save(proposed, tenantId).toCompletableFuture().join();
        try (Connection connection = dataSource().getConnection();
             var statement = connection.prepareStatement(
                     "SELECT tenant_id, event_type, payload->>'eventId' AS event_id "
                             + "FROM platform_outbox WHERE event_type = ?")) {
            statement.setString(1, "organization.party-association.changed");
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(tenantId, rows.getObject("tenant_id", UUID.class));
                assertEquals("organization.party-association.changed", rows.getString("event_type"));
                assertEquals(1, rows.getRow());
                assertFalse(rows.next(), "exact replay must not append another outbox event");
            }
        }
    }


    @Test
    void outboxFailureRollsBackAssociationAndHistory() throws Exception {
        var repository = repository();
        UUID tenantId = UUID.randomUUID();
        var proposed = OrganizationPartyAssociation.propose(
                UUID.randomUUID(), OrganizationId.generate(), PartyId.generate(),
                T0, "operator-1", "registry:atomicity");

        try (Connection connection = dataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE platform_outbox");
        }

        assertThrows(RuntimeException.class,
                () -> repository.save(proposed, tenantId).toCompletableFuture().join());

        assertTrue(repository.findById(proposed.id()).toCompletableFuture().join().isEmpty(),
                "association insert must roll back when its outbox insert fails");
        assertTrue(repository.findHistory(proposed.id()).toCompletableFuture().join().isEmpty(),
                "history insert must roll back when its outbox insert fails");
    }

    @Test
    void eachLifecycleTransitionGetsItsOwnTenantScopedOutboxEvent() throws Exception {
        var repository = repository();
        UUID tenantId = UUID.randomUUID();
        var proposed = OrganizationPartyAssociation.propose(
                UUID.randomUUID(), OrganizationId.generate(), PartyId.generate(),
                T0, "operator-1", null);
        repository.save(proposed, tenantId).toCompletableFuture().join();

        var active = proposed.activate(T0.plusSeconds(10), "verifier-1");
        repository.save(active, tenantId).toCompletableFuture().join();

        try (Connection connection = dataSource().getConnection();
             var statement = connection.prepareStatement("""
                     SELECT o.event_id, o.tenant_id, o.payload->>'eventId' AS envelope_event_id,
                            o.payload->'payload'->>'eventType' AS payload_event_type,
                            o.payload->'payload'->'data'->>'transition' AS transition,
                            o.payload->'payload'->'data'->>'associationId' AS association_id
                     FROM platform_outbox o
                     WHERE o.event_type = ?
                     ORDER BY o.created_at, o.event_id
                     """)) {
            statement.setString(1, "organization.party-association.changed");
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                UUID proposedEventId = rows.getObject("event_id", UUID.class);
                assertEquals(tenantId, rows.getObject("tenant_id", UUID.class));
                assertEquals(proposedEventId.toString(), rows.getString("envelope_event_id"));
                assertEquals("organization.party-association.changed", rows.getString("payload_event_type"));
                assertEquals("PROPOSED", rows.getString("transition"));
                assertEquals(proposed.id().toString(), rows.getString("association_id"));

                assertTrue(rows.next());
                UUID activatedEventId = rows.getObject("event_id", UUID.class);
                assertNotEquals(proposedEventId, activatedEventId);
                assertEquals(tenantId, rows.getObject("tenant_id", UUID.class));
                assertEquals("PENDING_VERIFICATION_TO_ACTIVE", rows.getString("transition"));
                assertEquals(proposed.id().toString(), rows.getString("association_id"));
                assertFalse(rows.next(), "one event is expected for each committed lifecycle transition");
            }
        }
    }

    private OrganizationPartyAssociationRepository repository() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        return new JdbcOrganizationPartyAssociationRepository(dataSource, Runnable::run);
    }

    private DataSource dataSource() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
    }

    private void migrate(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS platform_outbox CASCADE");
            statement.execute("DROP TABLE IF EXISTS organization_party_association_history CASCADE");
            statement.execute("DROP TABLE IF EXISTS organization_party_associations CASCADE");
            executeMigration(statement, "db/migration/V1__organization_party_associations.sql");
            executeMigration(statement, "db/migration/V2__organization_party_association_history_immutability.sql");
            statement.execute("""
                    CREATE TABLE platform_outbox (
                        entry_id uuid PRIMARY KEY, event_id uuid NOT NULL UNIQUE, tenant_id uuid,
                        event_type text NOT NULL, payload jsonb NOT NULL, created_at timestamptz NOT NULL,
                        available_at timestamptz NOT NULL, attempts integer NOT NULL DEFAULT 0,
                        locked_until timestamptz, published_at timestamptz, last_error text)
                    """);
        }
    }

    private void executeMigration(Statement statement, String resource) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "Missing migration resource " + resource);
            statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
