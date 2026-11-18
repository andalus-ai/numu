package tech.kayys.numou.organization.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationMembership;
import tech.kayys.numou.organization.domain.OrganizationMembershipId;
import tech.kayys.numou.organization.domain.OrganizationMembershipStatus;
import tech.kayys.numou.organization.spi.OrganizationMembershipRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class AcceptOrganizationInvitationHandlerTest {

    @Test
    void authenticatedIntendedUserCanAcceptTenantScopedInvitation() {
        var tenant = TenantId.generate();
        var user = UserId.newId();
        var membership = OrganizationMembership.invite(
                OrganizationMembershipId.generate(), OrganizationId.generate(), user);
        var repository = new InMemoryMembershipPort(tenant, membership);
        var handler = new AcceptOrganizationInvitationHandler(repository);

        handler.handle(new AcceptOrganizationInvitationCommand(tenant, membership.getId(), user))
                .toCompletableFuture().join();

        assertTrue(repository.membership.isActive());
        assertEquals(OrganizationMembershipStatus.ACTIVE, repository.membership.status());
    }

    @Test
    void anotherAuthenticatedUserCannotAcceptInvitation() {
        var tenant = TenantId.generate();
        var intendedUser = UserId.newId();
        var membership = OrganizationMembership.invite(
                OrganizationMembershipId.generate(), OrganizationId.generate(), intendedUser);
        var repository = new InMemoryMembershipPort(tenant, membership);
        var handler = new AcceptOrganizationInvitationHandler(repository);

        assertThrows(RuntimeException.class, () -> handler.handle(new AcceptOrganizationInvitationCommand(
                tenant, membership.getId(), UserId.newId())).toCompletableFuture().join());
        assertEquals(OrganizationMembershipStatus.PENDING, repository.membership.status());
    }

    @Test
    void invitationFromAnotherTenantIsNotFound() {
        var tenant = TenantId.generate();
        var otherTenant = TenantId.generate();
        var membership = OrganizationMembership.invite(
                OrganizationMembershipId.generate(), OrganizationId.generate(), UserId.newId());
        var repository = new InMemoryMembershipPort(tenant, membership);
        var handler = new AcceptOrganizationInvitationHandler(repository);

        assertThrows(RuntimeException.class, () -> handler.handle(new AcceptOrganizationInvitationCommand(
                otherTenant, membership.getId(), membership.userId())).toCompletableFuture().join());
        assertEquals(OrganizationMembershipStatus.PENDING, repository.membership.status());
    }

    private static final class InMemoryMembershipPort implements OrganizationMembershipRepository {
        private final TenantId tenant;
        private OrganizationMembership membership;
        private final Map<OrganizationMembershipId, OrganizationMembership> rows = new ConcurrentHashMap<>();

        private InMemoryMembershipPort(TenantId tenant, OrganizationMembership membership) {
            this.tenant = tenant;
            this.membership = membership;
            rows.put(membership.getId(), membership);
        }

        @Override
        public CompletionStage<OrganizationMembership> save(OrganizationMembership value) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public CompletionStage<OrganizationMembership> save(OrganizationMembership value, TenantId tenantId) {
            if (!tenant.equals(tenantId)) return CompletableFuture.failedFuture(new IllegalArgumentException());
            membership = value;
            rows.put(value.getId(), value);
            return CompletableFuture.completedFuture(value);
        }

        @Override
        public CompletionStage<Optional<OrganizationMembership>> findById(OrganizationMembershipId id) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public CompletionStage<Optional<OrganizationMembership>> findByTenantAndId(
                TenantId tenantId, OrganizationMembershipId id) {
            return CompletableFuture.completedFuture(tenant.equals(tenantId)
                    ? Optional.ofNullable(rows.get(id)) : Optional.empty());
        }

        @Override
        public CompletionStage<Optional<OrganizationMembership>> findByOrganizationAndUser(
                OrganizationId organizationId, UserId userId) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public CompletionStage<Optional<OrganizationMembership>> findByTenantAndOrganizationAndUser(
                TenantId tenantId, OrganizationId organizationId, UserId userId) {
            return CompletableFuture.completedFuture(tenant.equals(tenantId)
                    ? rows.values().stream().filter(row -> row.organizationId().equals(organizationId)
                            && row.userId().equals(userId)).findFirst() : Optional.empty());
        }

        @Override
        public CompletionStage<Boolean> existsByTenantAndId(TenantId tenantId, OrganizationMembershipId id) {
            return CompletableFuture.completedFuture(tenant.equals(tenantId) && rows.containsKey(id));
        }

        @Override
        public CompletionStage<Boolean> existsById(OrganizationMembershipId id) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public CompletionStage<Void> delete(OrganizationMembership value) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public CompletionStage<Void> deleteById(OrganizationMembershipId id) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
    }
}
