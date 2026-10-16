package tech.kayys.numou.construction.spi.variation;

import tech.kayys.numou.construction.domain.variation.ChangeOrderItem;
import tech.kayys.numou.construction.domain.variation.ChangeOrderItemId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ChangeOrderItemRepository extends Repository<ChangeOrderItem, ChangeOrderItemId> {
    CompletionStage<List<ChangeOrderItem>> findByChangeOrderId(UUID changeOrderId);
}
