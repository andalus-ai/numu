package tech.kayys.numou.scope.spi;

import tech.kayys.numou.scope.domain.BreakdownNode;
import tech.kayys.numou.scope.domain.BreakdownNodeId;
import tech.kayys.numou.scope.domain.BreakdownStructureId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for nodes in neutral breakdown structures.
 *
 * <p>Tree-wide invariants such as parent membership, cycles, and sibling-code
 * uniqueness must be validated by the application boundary using a complete
 * structure view before a structural write is committed.</p>
 */
public interface BreakdownNodeRepository {
    CompletionStage<Optional<BreakdownNode>> findById(BreakdownNodeId id);

    CompletionStage<List<BreakdownNode>> findByStructureId(BreakdownStructureId structureId);

    CompletionStage<List<BreakdownNode>> findByParentId(
            BreakdownStructureId structureId, BreakdownNodeId parentId);

    CompletionStage<BreakdownNode> save(BreakdownNode node);

    CompletionStage<Void> delete(BreakdownNodeId id);
}
