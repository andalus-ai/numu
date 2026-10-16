package tech.kayys.syirkah.project.spi.port;

import tech.kayys.syirkah.foundation.domain.repository.Repository;
import tech.kayys.syirkah.project.domain.commercial.ChangeOrderId;
import tech.kayys.syirkah.project.domain.commercial.ChangeOrder;
import tech.kayys.syirkah.project.domain.commercial.ProjectContractId;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Persistence port for change orders. */
public interface ChangeOrderRepository
        extends Repository<ChangeOrder, ChangeOrderId> {

    CompletionStage<Optional<ChangeOrder>> findByNumber(
            ProjectId projectId,
            String number
    );

    CompletionStage<List<ChangeOrder>> findByContractId(
            ProjectContractId contractId
    );
}