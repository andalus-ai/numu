package tech.kayys.syirkah.organization.spi;

import tech.kayys.syirkah.foundation.domain.repository.Repository;
import tech.kayys.syirkah.organization.domain.OrganizationUnit;
import tech.kayys.syirkah.organization.domain.OrganizationUnitId;

/** Repository port for the {@link OrganizationUnit} aggregate. */
public interface OrganizationUnitRepository extends Repository<OrganizationUnit, OrganizationUnitId> {
}
