package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.employment.OrganizationRef;
import tech.kayys.numou.workforce.domain.position.Position;
import tech.kayys.numou.workforce.domain.position.PositionId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for the {@link Position} aggregate root.
 */
public interface PositionRepository extends Repository<Position, PositionId> {

    /**
     * Finds all positions in an organization.
     */
    CompletionStage<List<Position>> findByOrganizationRef(OrganizationRef organizationRef);
}
