package tech.kayys.numou.organization.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationMembership;
import tech.kayys.numou.organization.domain.OrganizationMembershipId;
import tech.kayys.numou.organization.domain.OrganizationMembershipStatus;
import tech.kayys.numou.organization.spi.OrganizationMembershipRepository;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MembershipOrganizationInvitationPolicyTest {
    @Test
    void permitsOnlyActiveOwnerOrAdminMembership() {
        assertDoesNotThrow(() -> policyFor(OrganizationMembership.Role.OWNER,
                OrganizationMembershipStatus.ACTIVE).requireCanInvite(
                TenantId.generate(), OrganizationId.generate(), UserId.newId()).toCompletableFuture().join());
        assertDoesNotThrow(() -> policyFor(OrganizationMembership.Role.ADMIN,
                OrganizationMembershipStatus.ACTIVE).requireCanInvite(
                TenantId.generate(), OrganizationId.generate(), UserId.newId()).toCompletableFuture().join());
    }

    @Test
    void rejectsDeveloperViewerAndSuspendedAdministrators() {
        assertSecurityFailure(policyFor(OrganizationMembership.Role.DEVELOPER,
                OrganizationMembershipStatus.ACTIVE));
        assertSecurityFailure(policyFor(OrganizationMembership.Role.VIEWER,
                OrganizationMembershipStatus.ACTIVE));
        assertSecurityFailure(policyFor(OrganizationMembership.Role.ADMIN,
                OrganizationMembershipStatus.SUSPENDED));
    }

    @Test
    void tenantScopedMembershipLookupIsUsed() {
        TenantId tenant = TenantId.generate();
        OrganizationId organization = OrganizationId.generate();
        UserId actor = UserId.newId();
        AtomicReference<Object[]> observed = new AtomicReference<>();
        OrganizationMembershipRepository repository = (OrganizationMembershipRepository) Proxy.newProxyInstance(
                OrganizationMembershipRepository.class.getClassLoader(),
                new Class<?>[]{OrganizationMembershipRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByTenantAndOrganizationAndUser")) {
                        observed.set(args);
                        return CompletableFuture.completedFuture(Optional.empty());
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        new MembershipOrganizationInvitationPolicy(repository).requireCanInvite(tenant, organization, actor)
                .toCompletableFuture().handle((ignored, error) -> null).join();
        assertNotNull(observed.get());
        assertEquals(tenant, observed.get()[0]);
        assertEquals(organization, observed.get()[1]);
        assertEquals(actor, observed.get()[2]);
    }

    private static MembershipOrganizationInvitationPolicy policyFor(
            OrganizationMembership.Role role, OrganizationMembershipStatus status) {
        TenantId tenant = TenantId.generate();
        OrganizationId organization = OrganizationId.generate();
        UserId actor = UserId.newId();
        OrganizationMembership membership = OrganizationMembership.restore(
                OrganizationMembershipId.generate(), organization, actor, status, role);
        OrganizationMembershipRepository repository = (OrganizationMembershipRepository) Proxy.newProxyInstance(
                OrganizationMembershipRepository.class.getClassLoader(),
                new Class<?>[]{OrganizationMembershipRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByTenantAndOrganizationAndUser")) {
                        assertEquals(tenant, args[0]);
                        assertEquals(organization, args[1]);
                        assertEquals(actor, args[2]);
                        return CompletableFuture.completedFuture(Optional.of(membership));
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return new MembershipOrganizationInvitationPolicy(repository) {
        };
    }

    private static void assertSecurityFailure(MembershipOrganizationInvitationPolicy policy) {
        CompletionStage<Void> result = policy.requireCanInvite(
                TenantId.generate(), OrganizationId.generate(), UserId.newId());
        assertThrows(SecurityException.class, () -> result.toCompletableFuture().join()
                .toCompletableFuture().join());
    }
}
