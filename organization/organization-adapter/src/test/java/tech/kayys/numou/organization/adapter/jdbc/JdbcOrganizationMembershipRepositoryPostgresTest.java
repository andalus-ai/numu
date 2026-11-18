package tech.kayys.numou.organization.adapter.jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationMembership;
import tech.kayys.numou.organization.domain.OrganizationMembershipId;
import tech.kayys.numou.organization.spi.OrganizationMembershipRepository;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcOrganizationMembershipRepositoryPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void tenantIsolationAndCrossTenantPairIndependence() throws Exception {
        var repository = repository();
        var tenantA = TenantId.generate();
        var tenantB = TenantId.generate();
        var org = OrganizationId.generate();
        var user = UserId.newId();
        var a = OrganizationMembership.invite(OrganizationMembershipId.generate(), org, user);
        var b = OrganizationMembership.invite(OrganizationMembershipId.generate(), org, user);
        repository.save(a, tenantA).toCompletableFuture().join();
        repository.save(b, tenantB).toCompletableFuture().join();

        assertTrue(repository.findByTenantAndId(tenantA, a.getId()).toCompletableFuture().join().isPresent());
        assertTrue(repository.findByTenantAndId(tenantB, a.getId()).toCompletableFuture().join().isEmpty());
        assertTrue(repository.existsByTenantAndId(tenantA, a.getId()).toCompletableFuture().join());
        assertFalse(repository.existsByTenantAndId(tenantB, a.getId()).toCompletableFuture().join());
        assertTrue(repository.findByTenantAndOrganizationAndUser(tenantA, org, user)
                .toCompletableFuture().join().isPresent());
        assertTrue(repository.findByTenantAndOrganizationAndUser(tenantB, org, user)
                .toCompletableFuture().join().isPresent());
    }

    @Test
    void databaseRejectsDuplicateMembershipAndSecondActiveOwner() throws Exception {
        var repository = repository();
        var tenant = TenantId.generate();
        var org = OrganizationId.generate();
        var user = UserId.newId();
        var first = OrganizationMembership.invite(OrganizationMembershipId.generate(), org, user);
        var duplicate = OrganizationMembership.invite(OrganizationMembershipId.generate(), org, user);
        repository.save(first, tenant).toCompletableFuture().join();
        assertThrows(RuntimeException.class, () -> repository.save(duplicate, tenant).toCompletableFuture().join());

        var ownerOrg = OrganizationId.generate();
        var ownerA = OrganizationMembership.createOwner(OrganizationMembershipId.generate(), ownerOrg, UserId.newId());
        var ownerB = OrganizationMembership.createOwner(OrganizationMembershipId.generate(), ownerOrg, UserId.newId());
        repository.save(ownerA, tenant).toCompletableFuture().join();
        assertThrows(RuntimeException.class, () -> repository.save(ownerB, tenant).toCompletableFuture().join());
    }

    @Test
    void optimisticVersionPreventsStaleTransition() throws Exception {
        var repository = repository();
        var tenant = TenantId.generate();
        var pending = OrganizationMembership.invite(
                OrganizationMembershipId.generate(), OrganizationId.generate(), UserId.newId());
        repository.save(pending, tenant).toCompletableFuture().join();
        var first = repository.findByTenantAndId(tenant, pending.getId()).toCompletableFuture().join().orElseThrow();
        var stale = repository.findByTenantAndId(tenant, pending.getId()).toCompletableFuture().join().orElseThrow();
        first.activate();
        repository.save(first, tenant).toCompletableFuture().join();
        stale.activate();
        stale.setUpdatedAt(stale.getUpdatedAt().plusSeconds(1));
        assertThrows(RuntimeException.class, () -> repository.save(stale, tenant).toCompletableFuture().join());
        var persisted = repository.findByTenantAndId(tenant, pending.getId()).toCompletableFuture().join().orElseThrow();
        assertEquals(1, persisted.getVersion());
        assertTrue(persisted.isActive());
    }

    @Test
    void unscopedSaveFailsClosed() throws Exception {
        var repository = repository();
        var membership = OrganizationMembership.invite(
                OrganizationMembershipId.generate(), OrganizationId.generate(), UserId.newId());
        assertThrows(RuntimeException.class, () -> repository.save(membership).toCompletableFuture().join());
        assertThrows(RuntimeException.class, () -> repository.findById(membership.getId()).toCompletableFuture().join());
        assertThrows(RuntimeException.class, () -> repository.existsById(membership.getId()).toCompletableFuture().join());
        assertThrows(RuntimeException.class, () -> repository.deleteById(membership.getId()).toCompletableFuture().join());
    }

    private OrganizationMembershipRepository repository() throws Exception {
        DataSource source = dataSource();
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS organization_memberships CASCADE");
            try (InputStream input = getClass().getClassLoader()
                    .getResourceAsStream("db/migration/V3__organization_memberships.sql")) {
                assertNotNull(input);
                statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return new JdbcOrganizationMembershipRepository(source, Runnable::run);
    }

    private DataSource dataSource() {
        var source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
    }
}
