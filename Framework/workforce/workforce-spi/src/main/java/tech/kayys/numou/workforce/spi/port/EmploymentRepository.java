package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.employment.Employment;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.employment.OrganizationRef;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for the {@link Employment} aggregate root.
 */
public interface EmploymentRepository extends Repository<Employment, EmploymentId> {

    /**
     * Finds all employments associated with a specific worker.
     */
    CompletionStage<List<Employment>> findByWorkerId(WorkerId workerId);

    /**
     * Finds all employments associated with an organization.
     */
    CompletionStage<List<Employment>> findByOrganizationRef(OrganizationRef organizationRef);
}
