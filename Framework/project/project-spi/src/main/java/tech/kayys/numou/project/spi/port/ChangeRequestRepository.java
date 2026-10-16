package tech.kayys.numou.project.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.project.domain.commercial.ChangeRequest;
import tech.kayys.numou.project.domain.commercial.ChangeRequestId;
import tech.kayys.numou.project.domain.commercial.ProjectContractId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Persistence port for change requests. */
public interface ChangeRequestRepository
        extends Repository<ChangeRequest, ChangeRequestId> {

    CompletionStage<List<ChangeRequest>> findByContractId(
            ProjectContractId contractId
    );
}