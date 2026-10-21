package tech.kayys.numou.identity.adapter.outbound.postgres;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.application.port.MembershipManagementPort;
import tech.kayys.numou.identity.domain.user.UserId;

/**
 * Persistence-level membership contract. Uses the configured test PostgreSQL
 * datasource and the actual Flyway schema/query, rather than a mocked port.
 */
@QuarkusTest
class PostgresMembershipManagementRepositoryTest {

    @Inject
    MembershipManagementPort membership;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void seedRows() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(3600);

        Panache.withTransaction(() -> Panache.getSession()
                .chain(session -> session.createNativeQuery(
                        "insert into tenants (id, name, status, version) values (:id, 'membership-test', 'ACTIVE', 0)")
                        .setParameter("id", tenantId).executeUpdate())
                .chain(() -> Panache.getSession().chain(session -> session.createNativeQuery(
                        "insert into identity_users (id, email, password_hash, display_name, status, version) "
                                + "values (:id, :email, 'test-hash', 'Membership Test', :status, 0)")
                        .setParameter("id", userId)
                        .setParameter("email", "membership-" + userId + "@example.invalid")
                        .setParameter("status", "ACTIVE").executeUpdate()))
                .chain(() -> Panache.getSession().chain(session -> session.createNativeQuery(
                        "insert into tenant_memberships (id, tenant_id, user_id, status, invited_at, invitation_expires_at, version) "
                                + "values (:id, :tenant, :user, :status, :invited, :expires, 0)")
                        .setParameter("id", UUID.randomUUID())
                        .setParameter("tenant", tenantId)
                        .setParameter("user", userId)
                        .setParameter("status", "ACTIVE")
                        .setParameter("invited", now)
                        .setParameter("expires", expiry)
                        .executeUpdate())))
                .await().indefinitely();
    }

    @Test
    void activeMembershipAndActiveUserAreAccepted() {
        assertTrue(isActive());
    }

    @Test
    void invitedSuspendedAndRevokedMembershipsAreDenied() {
        for (String status : new String[] {"INVITED", "SUSPENDED", "REVOKED"}) {
            setMembershipStatus(status);
            assertFalse(isActive(), "Expected " + status + " membership to be denied");
        }
    }

    @Test
    void deactivatedUserIsDeniedEvenWhenMembershipIsActive() {
        Panache.withTransaction(() -> Panache.getSession().chain(session -> session.createNativeQuery(
                "update identity_users set status = 'DEACTIVATED' where id = :id")
                .setParameter("id", userId).executeUpdate())).await().indefinitely();

        assertFalse(isActive());
    }

    private boolean isActive() {
        return membership.isActive(TenantId.of(tenantId), UserId.of(userId)).await().indefinitely();
    }

    private void setMembershipStatus(String status) {
        Panache.withTransaction(() -> Panache.getSession().chain(session -> session.createNativeQuery(
                "update tenant_memberships set status = :status where tenant_id = :tenant and user_id = :user")
                .setParameter("status", status)
                .setParameter("tenant", tenantId)
                .setParameter("user", userId)
                .executeUpdate())).await().indefinitely();
    }
}
