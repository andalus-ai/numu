package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.position.PositionAssignment;
import tech.kayys.numou.workforce.domain.position.PositionAssignmentId;
import tech.kayys.numou.workforce.domain.position.PositionId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for the {@link PositionAssignment} aggregate root.
 */
public interface PositionAssignmentRepository extends Repository<PositionAssignment, PositionAssignmentId> {

    /**
     * Finds all position assignments for a given worker.
     */
    CompletionStage<List<PositionAssignment>> findByWorkerId(WorkerId workerId);

    /**
     * Finds all assignments for a given position.
     */
    CompletionStage<List<PositionAssignment>> findByPositionId(PositionId positionId);
}
