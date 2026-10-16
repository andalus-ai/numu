package tech.kayys.syirkah.construction.spi.variation;

import tech.kayys.syirkah.construction.domain.variation.ChangeOrder;
import tech.kayys.syirkah.construction.domain.variation.ChangeOrderId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ChangeOrderRepository extends Repository<ChangeOrder, ChangeOrderId> {
    CompletionStage<List<ChangeOrder>> findByProjectId(UUID projectId);
    CompletionStage<Optional<ChangeOrder>> findByOrderNumber(UUID projectId, String orderNumber);
}
