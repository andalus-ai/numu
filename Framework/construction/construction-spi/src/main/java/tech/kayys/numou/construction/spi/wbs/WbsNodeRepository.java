package tech.kayys.syirkah.construction.spi.wbs;

import tech.kayys.syirkah.construction.domain.wbs.WbsNode;
import tech.kayys.syirkah.construction.domain.wbs.WbsNodeId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface WbsNodeRepository extends Repository<WbsNode, WbsNodeId> {
    CompletionStage<List<WbsNode>> findByProjectId(UUID projectId);
    CompletionStage<List<WbsNode>> findByParentNodeId(UUID parentNodeId);
}
