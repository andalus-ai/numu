package tech.kayys.numou.construction.spi.variation;

import tech.kayys.numou.construction.domain.variation.ChangeOrder;
import tech.kayys.numou.construction.domain.variation.ChangeOrderId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ChangeOrderRepository extends Repository<ChangeOrder, ChangeOrderId> {
    CompletionStage<List<ChangeOrder>> findByProjectId(UUID projectId);
    CompletionStage<Optional<ChangeOrder>> findByOrderNumber(UUID projectId, String orderNumber);
}
