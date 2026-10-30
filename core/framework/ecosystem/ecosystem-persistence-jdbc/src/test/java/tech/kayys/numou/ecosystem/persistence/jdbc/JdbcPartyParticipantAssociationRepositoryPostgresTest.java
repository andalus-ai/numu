package tech.kayys.numou.ecosystem.persistence.jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyParticipantAssociation;
import tech.kayys.numou.ecosystem.domain.party.PartyParticipantRole;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcPartyParticipantAssociationRepositoryPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void persistsTenantScopedRolesAndRevocationHistory() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        var repository = new JdbcPartyParticipantAssociationRepository(dataSource);
        TenantId tenant = TenantId.generate();
        TenantId otherTenant = TenantId.generate();
        PartyId party = PartyId.generate();
        insertParty(dataSource, party);
        ParticipantId participant = ParticipantId.generate();
        Instant start = Instant.parse("2026-10-30T00:00:00Z");
        var association = PartyParticipantAssociation.establish(UUID.randomUUID(), tenant, party,
                participant, PartyParticipantRole.CUSTOMER, start);

        assertEquals(association, repository.save(association).await().indefinitely());
        assertEquals(association, repository.findActive(tenant, party, participant, PartyParticipantRole.CUSTOMER)
                .await().indefinitely().orElseThrow());
        assertTrue(repository.findActiveByTenantAndParty(otherTenant, party).await().indefinitely().isEmpty());
        assertTrue(repository.findByTenantAndId(otherTenant, association.id()).await().indefinitely().isEmpty());

        var partnerRole = PartyParticipantAssociation.establish(UUID.randomUUID(), tenant, party,
                participant, PartyParticipantRole.PARTNER, start);
        repository.save(partnerRole).await().indefinitely();
        assertEquals(2, repository.findActiveByTenantAndParty(tenant, party).await().indefinitely().size());

        var revoked = association.revoke(start.plusSeconds(90));
        repository.save(revoked).await().indefinitely();
        assertTrue(repository.findActive(tenant, party, participant, PartyParticipantRole.CUSTOMER)
                .await().indefinitely().isEmpty());
        assertEquals(revoked, repository.findByTenantAndId(tenant, association.id()).await().indefinitely().orElseThrow());
        assertEquals(revoked, repository.save(revoked).await().indefinitely());
    }

    @Test
    void databaseEnforcesOneActivePartyParticipantRolePerTenant() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        var repository = new JdbcPartyParticipantAssociationRepository(dataSource);
        TenantId tenant = TenantId.generate();
        PartyId party = PartyId.generate();
        insertParty(dataSource, party);
        ParticipantId participant = ParticipantId.generate();
        Instant start = Instant.parse("2026-10-30T00:00:00Z");
        var first = PartyParticipantAssociation.establish(UUID.randomUUID(), tenant, party,
                participant, PartyParticipantRole.CUSTOMER, start);
        var duplicate = PartyParticipantAssociation.establish(UUID.randomUUID(), tenant, party,
                participant, PartyParticipantRole.CUSTOMER, start);
        repository.save(first).await().indefinitely();
        assertThrows(IllegalStateException.class, () -> repository.save(duplicate).await().indefinitely());
    }

    private DataSource dataSource() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
    }

    private void insertParty(DataSource dataSource, PartyId partyId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO ecosystem_party (party_id, kind, status, created_at, updated_at) "
                    + "VALUES ('" + partyId.value() + "', 'PERSON', 'ACTIVE', now(), now())");
        }
    }

    private void migrate(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS ecosystem_party_participant_associations CASCADE");
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
        String sql;
        try (var input = getClass().getClassLoader().getResourceAsStream(
                "db/migration/V3__party_participant_associations.sql")) {
            assertNotNull(input);
            sql = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        sql = sql.replace("party_id UUID NOT NULL REFERENCES ecosystem_party(party_id)",
                "party_id UUID NOT NULL");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
