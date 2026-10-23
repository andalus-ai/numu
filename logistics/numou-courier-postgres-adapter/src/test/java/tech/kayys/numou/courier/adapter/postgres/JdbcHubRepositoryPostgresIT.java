package tech.kayys.numou.courier.adapter.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationUnitId;

import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcHubRepositoryPostgresIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static JdbcHubRepository repository;

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        repository = new JdbcHubRepository(dataSource());
    }

    @Test
    void tenantCannotReadOrDeleteAnotherTenantsHub() {
        TenantId owner = TenantId.generate();
        TenantId otherTenant = TenantId.generate();
        Hub hub = hub(owner, "JKT-01");

        repository.save(owner, hub).toCompletableFuture().join();

        assertTrue(repository.findById(otherTenant, hub.id()).toCompletableFuture().join().isEmpty());
        repository.delete(otherTenant, hub.id()).toCompletableFuture().join();
        assertTrue(repository.findById(owner, hub.id()).toCompletableFuture().join().isPresent());
    }

    @Test
    void hubCodesAreUniqueWithinATenantButMayRepeatAcrossTenants() {
        TenantId tenantA = TenantId.generate();
        TenantId tenantB = TenantId.generate();
        repository.save(tenantA, hub(tenantA, "DEPOT-01")).toCompletableFuture().join();
        repository.save(tenantB, hub(tenantB, "DEPOT-01")).toCompletableFuture().join();

        CompletionException failure = assertThrows(CompletionException.class,
                () -> repository.save(tenantA, hub(tenantA, "DEPOT-01")).toCompletableFuture().join());

        assertInstanceOf(JdbcHubRepository.HubCodeConflictException.class, failure.getCause());
    }

    private static Hub hub(TenantId tenantId, String code) {
        return Hub.create(HubId.generate(), tenantId, OrganizationId.generate(), OrganizationUnitId.generate(),
                code, "Test Hub", HubType.ORIGIN_HUB,
                new HubAddress("1 Test Street", null, "Jakarta", null, null, "ID", -6.2, 106.8),
                ZoneId.of("Asia/Jakarta"), Instant.now());
    }

    private static javax.sql.DataSource dataSource() {
        org.postgresql.ds.PGSimpleDataSource dataSource = new org.postgresql.ds.PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }
}
