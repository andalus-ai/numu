package tech.kayys.numou.ecosystem.persistence.jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyIdentityAssociation;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcPartyIdentityAssociationRepositoryPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void persistsTenantScopedAssociationAndKeepsRevocationHistory() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        var repository = new JdbcPartyIdentityAssociationRepository(dataSource);
        TenantId tenant = TenantId.generate();
        TenantId otherTenant = TenantId.generate();
        PartyId party = PartyId.generate();
        var identity = new IdentitySubjectRef("https://issuer.example", "subject-123");
        Instant linkedAt = Instant.parse("2026-10-30T00:00:00Z");
        PartyIdentityAssociation active = PartyIdentityAssociation.link(
                UUID.randomUUID(), tenant, party, identity, linkedAt);

        assertEquals(active, repository.save(active).await().indefinitely());
        assertEquals(active, repository.findActiveByTenantAndIdentity(tenant, identity)
                .await().indefinitely().orElseThrow());
        assertTrue(repository.findActiveByTenantAndIdentity(otherTenant, identity)
                .await().indefinitely().isEmpty());
        assertTrue(repository.findByTenantAndParty(otherTenant, party)
                .await().indefinitely().isEmpty());

        // The same issuer/subject pair may independently exist in another tenant.
        var otherTenantLink = PartyIdentityAssociation.link(UUID.randomUUID(), otherTenant, party,
                identity, linkedAt);
        assertEquals(otherTenantLink, repository.save(otherTenantLink).await().indefinitely());
        assertEquals(otherTenantLink, repository.findActiveByTenantAndIdentity(otherTenant, identity)
                .await().indefinitely().orElseThrow());

        PartyIdentityAssociation revoked = active.revoke(linkedAt.plusSeconds(90));
        assertEquals(revoked, repository.save(revoked).await().indefinitely());
        assertTrue(repository.findActiveByTenantAndIdentity(tenant, identity)
                .await().indefinitely().isEmpty());
        assertEquals(1, repository.findByTenantAndParty(tenant, party)
                .await().indefinitely().size());
        assertEquals(revoked, repository.findByTenantAndParty(tenant, party)
                .await().indefinitely().getFirst());

        // Replaying the exact revocation is safe and does not create another history row.
        assertEquals(revoked, repository.save(revoked).await().indefinitely());
        assertEquals(1, repository.findByTenantAndParty(tenant, party)
                .await().indefinitely().size());

        // Revocation releases the active-subject key; a new, separately authorized
        // association can be created without erasing the prior link's audit history.
        var replacement = PartyIdentityAssociation.link(UUID.randomUUID(), tenant, PartyId.generate(),
                identity, linkedAt.plusSeconds(120));
        assertEquals(replacement, repository.save(replacement).await().indefinitely());
        assertEquals(replacement, repository.findActiveByTenantAndIdentity(tenant, identity)
                .await().indefinitely().orElseThrow());
        assertEquals(1, repository.findByTenantAndParty(tenant, party)
                .await().indefinitely().size());
    }

    @Test
    void databasePreventsTwoPartiesOwningOneActiveSubjectInTheSameTenant() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        var repository = new JdbcPartyIdentityAssociationRepository(dataSource);
        TenantId tenant = TenantId.generate();
        var identity = new IdentitySubjectRef("issuer", "same-subject");
        Instant linkedAt = Instant.parse("2026-10-30T00:00:00Z");
        var first = PartyIdentityAssociation.link(UUID.randomUUID(), tenant, PartyId.generate(), identity, linkedAt);
        var second = PartyIdentityAssociation.link(UUID.randomUUID(), tenant, PartyId.generate(), identity, linkedAt);

        repository.save(first).await().indefinitely();
        assertThrows(IllegalStateException.class,
                () -> repository.save(second).await().indefinitely());
        assertEquals(1, repository.findByTenantAndParty(tenant, first.partyId())
                .await().indefinitely().size());
    }

    @Test
    void associationIdCannotBeReassignedToAnotherPartyOrTenant() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        var repository = new JdbcPartyIdentityAssociationRepository(dataSource);
        TenantId tenant = TenantId.generate();
        Instant linkedAt = Instant.parse("2026-10-30T00:00:00Z");
        var original = PartyIdentityAssociation.link(UUID.randomUUID(), tenant, PartyId.generate(),
                new IdentitySubjectRef("issuer", "subject"), linkedAt);
        repository.save(original).await().indefinitely();

        var reassigned = PartyIdentityAssociation.link(original.id(), tenant, PartyId.generate(),
                original.identity(), linkedAt);
        assertThrows(IllegalArgumentException.class,
                () -> repository.save(reassigned).await().indefinitely());

        var crossTenant = PartyIdentityAssociation.link(original.id(), TenantId.generate(), original.partyId(),
                original.identity(), linkedAt);
        assertThrows(IllegalStateException.class,
                () -> repository.save(crossTenant).await().indefinitely());
    }

    private DataSource dataSource() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
    }

    private void migrate(DataSource dataSource) throws IOException, SQLException {
        String resource = "db/migration/V1__party_identity_associations.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "Missing migration resource " + resource);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                // Test methods share the static Testcontainers database; reset this adapter's
                // isolated schema before applying its single-version migration.
                statement.execute("DROP TABLE IF EXISTS ecosystem_party_identity_associations CASCADE");
                statement.execute(sql);
            }
        }
    }
}
