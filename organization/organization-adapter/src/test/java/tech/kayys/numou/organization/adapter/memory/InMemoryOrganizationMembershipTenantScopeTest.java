package tech.kayys.numou.organization.adapter.memory;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationMembership;
import tech.kayys.numou.organization.domain.OrganizationMembershipId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryOrganizationMembershipTenantScopeTest {

    @Test
    void tenantScopedLookupDoesNotExposeMembershipToAnotherTenant() {
        var repository = new InMemoryOrganizationMembershipRepository();
        var tenantA = new TenantId(UUID.randomUUID());
        var tenantB = new TenantId(UUID.randomUUID());
        var organizationId = OrganizationId.generate();
        var userId = new UserId(UUID.randomUUID());
        var membership = OrganizationMembership.invite(
                OrganizationMembershipId.generate(), organizationId, userId);

        repository.save(membership, tenantA).toCompletableFuture().join();

        assertTrue(repository.findByTenantAndId(tenantA, membership.getId())
                .toCompletableFuture().join().isPresent());
        assertTrue(repository.findByTenantAndId(tenantB, membership.getId())
                .toCompletableFuture().join().isEmpty());
        assertTrue(repository.findByTenantAndOrganizationAndUser(tenantB, organizationId, userId)
                .toCompletableFuture().join().isEmpty());
    }

    @Test
    void sameOrganizationAndUserMayExistInDifferentTenants() {
        var repository = new InMemoryOrganizationMembershipRepository();
        var tenantA = new TenantId(UUID.randomUUID());
        var tenantB = new TenantId(UUID.randomUUID());
        var organizationId = OrganizationId.generate();
        var userId = new UserId(UUID.randomUUID());
        var membershipA = OrganizationMembership.invite(
                OrganizationMembershipId.generate(), organizationId, userId);
        var membershipB = OrganizationMembership.invite(
                OrganizationMembershipId.generate(), organizationId, userId);

        repository.save(membershipA, tenantA).toCompletableFuture().join();
        repository.save(membershipB, tenantB).toCompletableFuture().join();

        assertTrue(repository.findByTenantAndOrganizationAndUser(tenantA, organizationId, userId)
                .toCompletableFuture().join().isPresent());
        assertTrue(repository.findByTenantAndOrganizationAndUser(tenantB, organizationId, userId)
                .toCompletableFuture().join().isPresent());
    }


    @Test
    void existenceCheckIsTenantScopedAndUnscopedAccessFailsClosed() {
        var repository = new InMemoryOrganizationMembershipRepository();
        var tenantA = TenantId.generate();
        var tenantB = TenantId.generate();
        var membership = OrganizationMembership.invite(
                OrganizationMembershipId.generate(), OrganizationId.generate(), new UserId(UUID.randomUUID()));

        repository.save(membership, tenantA).toCompletableFuture().join();

        assertTrue(repository.existsByTenantAndId(tenantA, membership.getId()).toCompletableFuture().join());
        assertFalse(repository.existsByTenantAndId(tenantB, membership.getId()).toCompletableFuture().join());
        assertThrows(RuntimeException.class, () -> repository.findById(membership.getId()).toCompletableFuture().join());
        assertThrows(RuntimeException.class, () -> repository.existsById(membership.getId()).toCompletableFuture().join());
        assertThrows(RuntimeException.class, () -> repository.deleteById(membership.getId()).toCompletableFuture().join());
    }

    @Test
    void duplicateMembershipWithinTenantIsRejected() {
        var repository = new InMemoryOrganizationMembershipRepository();
        var tenantId = new TenantId(UUID.randomUUID());
        var organizationId = OrganizationId.generate();
        var userId = new UserId(UUID.randomUUID());
        var first = OrganizationMembership.invite(
                OrganizationMembershipId.generate(), organizationId, userId);
        var duplicate = OrganizationMembership.invite(
                OrganizationMembershipId.generate(), organizationId, userId);

        repository.save(first, tenantId).toCompletableFuture().join();

        assertThrows(RuntimeException.class, () -> repository.save(duplicate, tenantId)
                .toCompletableFuture().join());
    }
}
