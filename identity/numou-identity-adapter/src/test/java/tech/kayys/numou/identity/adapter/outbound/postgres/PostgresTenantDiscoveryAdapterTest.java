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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(IdentityPostgresTestResource.class)
class PostgresTenantDiscoveryAdapterTest {
    @Inject
    TenantDiscoveryPort discovery;

    private UUID userId;
    private UUID inactiveUserId;
    private UUID activeTenant;
    private UUID secondActiveTenant;
    private UUID revokedTenant;

    @BeforeEach
    void seedMemberships() {
        userId = UUID.randomUUID();
        inactiveUserId = UUID.randomUUID();
        activeTenant = UUID.randomUUID();
        secondActiveTenant = UUID.randomUUID();
        revokedTenant = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(3600);

        Panache.withTransaction(() -> Panache.getSession()
                .chain(session -> session.createNativeQuery(
                        "insert into tenants (id, name, status, version) values (:id, :name, 'ACTIVE', 0)")
                        .setParameter("id", activeTenant)
                        .setParameter("name", "discovery-" + activeTenant)
                        .executeUpdate())
                .chain(() -> Panache.getSession().chain(session -> session.createNativeQuery(
                        "insert into tenants (id, name, status, version) values (:id, :name, 'ACTIVE', 0)")
                        .setParameter("id", secondActiveTenant)
                        .setParameter("name", "discovery-" + secondActiveTenant)
                        .executeUpdate()))
                .chain(() -> Panache.getSession().chain(session -> session.createNativeQuery(
                        "insert into tenants (id, name, status, version) values (:id, :name, 'ACTIVE', 0)")
                        .setParameter("id", revokedTenant)
                        .setParameter("name", "discovery-" + revokedTenant)
                        .executeUpdate()))
                .chain(() -> Panache.getSession().chain(session -> session.createNativeQuery(
                        "insert into identity_users (id, email, password_hash, display_name, status, version) "
                                + "values (:id, :email, 'test-hash', 'Discovery Test', :status, 0)")
                        .setParameter("id", userId)
                        .setParameter("email", "discovery-" + userId + "@example.invalid")
                        .setParameter("status", "ACTIVE")
                        .executeUpdate()))
                .chain(() -> Panache.getSession().chain(session -> session.createNativeQuery(
                        "insert into identity_users (id, email, password_hash, display_name, status, version) "
                                + "values (:id, :email, 'test-hash', 'Inactive Discovery Test', 'INACTIVE', 0)")
                        .setParameter("id", inactiveUserId)
                        .setParameter("email", "discovery-" + inactiveUserId + "@example.invalid")
                        .executeUpdate()))
                .chain(() -> insertMembership(activeTenant, userId, "ACTIVE", now, expiry))
                .chain(() -> insertMembership(secondActiveTenant, userId, "ACTIVE", now, expiry))
                .chain(() -> insertMembership(revokedTenant, userId, "REVOKED", now, expiry))
                .chain(() -> insertMembership(activeTenant, inactiveUserId, "ACTIVE", now, expiry)))
                .await().indefinitely();
    }

    private io.smallrye.mutiny.Uni<Integer> insertMembership(
            UUID tenantId, UUID memberUserId, String status, Instant invitedAt, Instant expiresAt) {
        return Panache.getSession().chain(session -> session.createNativeQuery(
                        "insert into tenant_memberships (id, tenant_id, user_id, status, invited_at, invitation_expires_at, version) "
                                + "values (:id, :tenant, :user, :status, :invited, :expires, 0)")
                .setParameter("id", UUID.randomUUID())
                .setParameter("tenant", tenantId)
                .setParameter("user", memberUserId)
                .setParameter("status", status)
                .setParameter("invited", invitedAt)
                .setParameter("expires", expiresAt)
                .executeUpdate());
    }

    @Test
    void returnsOnlyActiveMembershipsInDeterministicOrder() {
        List<UUID> result = discovery.activeTenantIds(UserId.of(userId)).await().indefinitely();
        assertEquals(Stream.of(activeTenant, secondActiveTenant)
                .sorted(Comparator.comparing(UUID::toString)).toList(), result);
    }

    @Test
    void excludesMembershipsBelongingToInactiveUsers() {
        assertEquals(List.of(), discovery.activeTenantIds(UserId.of(inactiveUserId)).await().indefinitely());
    }
}
