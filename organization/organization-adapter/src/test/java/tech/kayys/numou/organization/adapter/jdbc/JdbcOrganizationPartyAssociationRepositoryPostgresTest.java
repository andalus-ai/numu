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
            statement.execute("DROP TABLE IF EXISTS organization_party_association_history CASCADE");
            statement.execute("DROP TABLE IF EXISTS organization_party_associations CASCADE");
            String resource = "db/migration/V1__organization_party_associations.sql";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input, "Missing migration resource " + resource);
                statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }
}
