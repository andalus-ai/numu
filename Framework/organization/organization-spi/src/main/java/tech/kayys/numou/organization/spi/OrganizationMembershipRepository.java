package tech.kayys.syirkah.organization.spi;

import tech.kayys.syirkah.foundation.domain.repository.Repository;
import tech.kayys.syirkah.identity.domain.user.UserId;
import tech.kayys.syirkah.organization.domain.OrganizationId;
import tech.kayys.syirkah.organization.domain.OrganizationMembership;
import tech.kayys.syirkah.organization.domain.OrganizationMembershipId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface OrganizationMembershipRepository
        extends Repository<OrganizationMembership, OrganizationMembershipId> {

    CompletionStage<Optional<OrganizationMembership>> findByOrganizationAndUser(
            OrganizationId organizationId, UserId userId);
}
