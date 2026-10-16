package tech.kayys.syirkah.construction.spi.boq;

import tech.kayys.syirkah.construction.domain.boq.BoqItem;
import tech.kayys.syirkah.construction.domain.boq.BoqItemId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface BoqItemRepository extends Repository<BoqItem, BoqItemId> {
    CompletionStage<List<BoqItem>> findByBoqId(UUID boqId);
    CompletionStage<List<BoqItem>> findByWbsNodeId(UUID wbsNodeId);
}
