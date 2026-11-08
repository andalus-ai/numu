package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.application.port.MembershipManagementPort;
import tech.kayys.numou.identity.application.port.TenantDiscoveryPort;
import tech.kayys.numou.identity.application.port.TenantTokenIssuerPort;
import tech.kayys.numou.identity.domain.user.UserId;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TenantSelectionServiceTest {

    @Test
    void discoversOnlyMembershipsReturnedByIdentity() {
        UUID userId = UUID.randomUUID();
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        TenantDiscoveryPort discovery = user -> {
            assertEquals(UserId.of(userId), user);
            return Uni.createFrom().item(List.of(tenantA, tenantB));
        };
        MembershipManagementPort memberships = (tenant, user) -> Uni.createFrom().item(false);
        TenantTokenIssuerPort issuer = new FakeIssuer();
        TenantSelectionService service = new TenantSelectionService(discovery, memberships, issuer);

        assertEquals(List.of(tenantA, tenantB),
                service.eligibleTenants(userId).await().indefinitely());
    }

    @Test
    void issuesTenantBoundTokenOnlyAfterMembershipRevalidation() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        AtomicInteger issued = new AtomicInteger();
        MembershipManagementPort memberships = (tenant, user) -> Uni.createFrom().item(
                tenant.equals(TenantId.of(tenantId)) && user.equals(UserId.of(userId)));
        TenantTokenIssuerPort issuer = new FakeIssuer() {
            @Override
            public String issueTenantAccessToken(UUID principalId, String displayName, UUID requestedTenant) {
                issued.incrementAndGet();
                assertEquals(userId, principalId);
                assertEquals(tenantId, requestedTenant);
                return "tenant-access-token";
            }
        };
        TenantSelectionService service = new TenantSelectionService(
                user -> Uni.createFrom().item(List.of(tenantId)), memberships, issuer);

        var response = service.exchange(userId, "Alice", tenantId).await().indefinitely();

        assertEquals("tenant-access-token", response.accessToken());
        assertEquals(1, issued.get());
    }

    @Test
    void doesNotIssueTokenForInactiveMembership() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        AtomicInteger issued = new AtomicInteger();
        MembershipManagementPort memberships = (tenant, user) -> Uni.createFrom().item(false);
        TenantTokenIssuerPort issuer = new FakeIssuer() {
            @Override
            public String issueTenantAccessToken(UUID principalId, String displayName, UUID requestedTenant) {
                issued.incrementAndGet();
                return "must-not-be-issued";
            }
        };
        TenantSelectionService service = new TenantSelectionService(
                user -> Uni.createFrom().item(List.of()), memberships, issuer);

        assertThrows(RuntimeException.class,
                () -> service.exchange(userId, "Alice", tenantId).await().indefinitely());
        assertEquals(0, issued.get());
    }

    @Test
    void rejectsMissingTenantBeforeMembershipLookup() {
        AtomicInteger checked = new AtomicInteger();
        MembershipManagementPort memberships = (tenant, user) -> {
            checked.incrementAndGet();
            return Uni.createFrom().item(true);
        };
        TenantSelectionService service = new TenantSelectionService(
                user -> Uni.createFrom().item(List.of()), memberships, new FakeIssuer());

        assertThrows(RuntimeException.class,
                () -> service.exchange(UUID.randomUUID(), "Alice", null).await().indefinitely());
        assertEquals(0, checked.get());
    }

    private static class FakeIssuer implements TenantTokenIssuerPort {
        @Override
        public String issueTenantSelectionToken(UUID principalId, String displayName) {
            return "selection-token";
        }

        @Override
        public String issueTenantAccessToken(UUID principalId, String displayName, UUID tenantId) {
            return "access-token";
        }
    }
}
