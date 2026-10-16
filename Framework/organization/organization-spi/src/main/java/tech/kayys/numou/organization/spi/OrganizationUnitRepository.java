package tech.kayys.numou.organization.spi;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.organization.domain.OrganizationUnit;
import tech.kayys.numou.organization.domain.OrganizationUnitId;

/** Repository port for the {@link OrganizationUnit} aggregate. */
public interface OrganizationUnitRepository extends Repository<OrganizationUnit, OrganizationUnitId> {
}
