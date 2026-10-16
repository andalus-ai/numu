package tech.kayys.syirkah.organization.spi;

import tech.kayys.syirkah.foundation.domain.repository.Repository;
import tech.kayys.syirkah.organization.domain.Organization;
import tech.kayys.syirkah.organization.domain.OrganizationId;

/** Repository port for the {@link Organization} aggregate. */
public interface OrganizationRepository extends Repository<Organization, OrganizationId> {
}
