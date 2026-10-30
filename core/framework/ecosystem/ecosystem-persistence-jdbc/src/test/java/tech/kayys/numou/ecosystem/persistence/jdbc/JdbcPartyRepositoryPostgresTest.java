package tech.kayys.numou.ecosystem.persistence.jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.ecosystem.domain.model.Party;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyKind;
import tech.kayys.numou.ecosystem.domain.party.PartyStatus;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcPartyRepositoryPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void persistsAndRehydratesCanonicalPartyWithoutTenantOwnership() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        var repository = new JdbcPartyRepository(dataSource);
        Party original = Party.register(PartyId.generate(), PartyKind.ORGANIZATION);

        assertEquals(original, repository.save(original).await().indefinitely());
        Party loaded = repository.findById(original.getId()).await().indefinitely().orElseThrow();

        assertEquals(original.getId(), loaded.getId());
        assertEquals(PartyKind.ORGANIZATION, loaded.kind());
        assertEquals(PartyStatus.ACTIVE, loaded.status());
        assertEquals(0, loaded.getVersion());
        assertNotNull(loaded.getCreatedAt());
        assertNotNull(loaded.getUpdatedAt());
    }

    @Test
    void persistsLifecycleTransitionsWithOptimisticVersioning() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        var repository = new JdbcPartyRepository(dataSource);
        Party original = Party.register(PartyId.generate(), PartyKind.PERSON);
        repository.save(original).await().indefinitely();

        Party firstRead = repository.findById(original.getId()).await().indefinitely().orElseThrow();
        Party staleRead = repository.findById(original.getId()).await().indefinitely().orElseThrow();

        firstRead.deactivate();
        repository.save(firstRead).await().indefinitely();
        assertEquals(PartyStatus.INACTIVE,
                repository.findById(original.getId()).await().indefinitely().orElseThrow().status());

        staleRead.deactivate();
        assertThrows(JdbcPartyRepository.OptimisticPartyConflictException.class,
                () -> repository.save(staleRead).await().indefinitely());

        Party reloaded = repository.findById(original.getId()).await().indefinitely().orElseThrow();
        reloaded.reactivate();
        repository.save(reloaded).await().indefinitely();
        Party active = repository.findById(original.getId()).await().indefinitely().orElseThrow();
        assertEquals(PartyStatus.ACTIVE, active.status());
        assertEquals(2, active.getVersion());
    }

    @Test
    void rejectsUnknownKindAtDatabaseBoundary() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            assertThrows(Exception.class, () -> statement.execute(
                    "INSERT INTO ecosystem_party (party_id, kind, status, created_at, updated_at, version) "
                            + "VALUES ('" + UUID.randomUUID() + "', 'UNKNOWN', 'ACTIVE', "
                            + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)"));
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
        String resource = "db/migration/V2__canonical_parties.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "Missing migration resource " + resource);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS ecosystem_party CASCADE");
                statement.execute(sql);
            }
        }
    }
}
