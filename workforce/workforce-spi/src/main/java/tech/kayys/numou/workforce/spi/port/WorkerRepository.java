package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.ref.PersonRef;
import tech.kayys.numou.workforce.domain.worker.Worker;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for the {@link Worker} aggregate root.
 */
public interface WorkerRepository extends Repository<Worker, WorkerId> {

    /**
     * Looks up a worker by their cross-domain person reference.
     */
    CompletionStage<Optional<Worker>> findByPersonRef(PersonRef personRef);
}
