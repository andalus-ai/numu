package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.ref.PersonRef;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.worker.Worker;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for the {@link Worker} aggregate root.
 */
public interface WorkerRepository extends Repository<Worker, WorkerId> {

    /** Looks up a worker by their cross-domain person reference within a tenant. */
    CompletionStage<Optional<Worker>> findByPersonRef(TenantId tenantId, PersonRef personRef);

    /** Finds a worker only when both its identifier and owning tenant match. */
    CompletionStage<Optional<Worker>> findById(TenantId tenantId, WorkerId workerId);

    /** Checks existence without disclosing whether an identifier belongs to another tenant. */
    CompletionStage<Boolean> existsById(TenantId tenantId, WorkerId workerId);

    /** Deletes a worker only when both its identifier and owning tenant match. */
    CompletionStage<Void> deleteById(TenantId tenantId, WorkerId workerId);
}
