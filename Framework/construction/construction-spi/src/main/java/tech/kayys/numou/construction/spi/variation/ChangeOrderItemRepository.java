package tech.kayys.syirkah.construction.spi.variation;

import tech.kayys.syirkah.construction.domain.variation.ChangeOrderItem;
import tech.kayys.syirkah.construction.domain.variation.ChangeOrderItemId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ChangeOrderItemRepository extends Repository<ChangeOrderItem, ChangeOrderItemId> {
    CompletionStage<List<ChangeOrderItem>> findByChangeOrderId(UUID changeOrderId);
}
