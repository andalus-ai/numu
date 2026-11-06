package tech.kayys.numou.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.identity.application.port.TenantDiscoveryPort;
import tech.kayys.numou.identity.domain.user.UserId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(IdentityPostgresTestResource.class)
class PostgresTenantDiscoveryAdapterTest {
    @Inject
    TenantDiscoveryPort discovery;

    private UUID userId;
    private UUID activeTenant;
    private UUID inactiveTenant;

    @BeforeEach
    void seedMemberships() {
        userId = UUID.randomUUID();
        activeTenant = UUID.randomUUID();
        inactiveTenant = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(3600);

        Panache.withTransaction(() -> Panache.getSession()
                .chain(session -> session.createNativeQuery(
                        "insert into tenants (id, name, status, version) values (:id, 'discovery-active', 'ACTIVE', 0)")
                        .setParameter("id", activeTenant).executeUpdate())
                .chain(() -> Panache.getSession().chain(session -> session.createNativeQuery(
                        "insert into tenants (id, name, status, version) values (:id, 'discovery-inactive', 'ACTIVE', 0)")
                        .setParameter("id", inactiveTenant).executeUpdate()))
                .chain(() -> Panache.getSession().chain(session -> session.createNativeQuery(
                        "insert into identity_users (id, email, password_hash, display_name, status, version) "
                                + "values (:id, :email, 'test-hash', 'Discovery Test', 'ACTIVE', 0)")
                        .setParameter("id", userId)
                        .setParameter("email", "discovery-" + userId + "@example.invalid")
                        .executeUpdate()))
                .chain(() -> Panache.getSession().chain(session -> session.createNativeQuery(
                        "insert into tenant_memberships (id, tenant_id, user_id, status, invited_at, invitation_expires_at, version) "
                                + "values (:id, :tenant, :user, :status, :invited, :expires, 0)")
                        .setParameter("id", UUID.randomUUID())
                        .setParameter("tenant", activeTenant)
                        .setParameter("user", userId)
                        .setParameter("status", "ACTIVE")
                        .setParameter("invited", now)
                        .setParameter("expires", expiry)
                        .executeUpdate()))
                .chain(() -> Panache.getSession().chain(session -> session.createNativeQuery(
                        "insert into tenant_memberships (id, tenant_id, user_id, status, invited_at, invitation_expires_at, version) "
                                + "values (:id, :tenant, :user, :status, :invited, :expires, 0)")
                        .setParameter("id", UUID.randomUUID())
                        .setParameter("tenant", inactiveTenant)
                        .setParameter("user", userId)
                        .setParameter("status", "REVOKED")
                        .setParameter("invited", now)
                        .setParameter("expires", expiry)
                        .executeUpdate())))
                .await().indefinitely();
    }

    @Test
    void returnsOnlyActiveMembershipsInDeterministicOrder() {
        List<UUID> result = discovery.activeTenantIds(UserId.of(userId)).await().indefinitely();
        assertEquals(List.of(activeTenant), result);
    }
}
