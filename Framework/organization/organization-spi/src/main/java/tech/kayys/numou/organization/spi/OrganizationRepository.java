package tech.kayys.numou.organization.spi;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.organization.domain.Organization;
import tech.kayys.numou.organization.domain.OrganizationId;

/** Repository port for the {@link Organization} aggregate. */
public interface OrganizationRepository extends Repository<Organization, OrganizationId> {
}
