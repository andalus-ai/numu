package tech.kayys.numou.scope.spi;

import tech.kayys.numou.scope.domain.BreakdownStructure;
import tech.kayys.numou.scope.domain.BreakdownStructureId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for neutral breakdown-structure metadata.
 *
 * <p>Implementations must preserve structure identity and lifecycle state.
 * Owner references are opaque strings; implementations must not interpret them
 * as project, construction, or any other industry-specific identifiers.</p>
 */
public interface BreakdownStructureRepository {
    CompletionStage<Optional<BreakdownStructure>> findById(BreakdownStructureId id);

    CompletionStage<Optional<BreakdownStructure>> findByOwnerReferenceAndCode(
            String ownerReference, String code);

    CompletionStage<BreakdownStructure> save(BreakdownStructure structure);
}
