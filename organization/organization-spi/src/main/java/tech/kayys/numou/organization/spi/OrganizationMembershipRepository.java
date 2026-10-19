package tech.kayys.numou.organization.spi;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationMembership;
import tech.kayys.numou.organization.domain.OrganizationMembershipId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface OrganizationMembershipRepository
        extends Repository<OrganizationMembership, OrganizationMembershipId> {

    CompletionStage<Optional<OrganizationMembership>> findByOrganizationAndUser(
            OrganizationId organizationId, UserId userId);
}
