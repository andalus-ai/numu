package tech.kayys.syirkah.commerce.promotion.spi.port;

import tech.kayys.syirkah.commerce.promotion.domain.Promotion;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionVersionId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for promotion definitions (product03.md application layer).
 *
 * <p>Bulk loading is the expected access pattern: the resolver returns
 * candidate version ids, then the application layer calls
 * {@link #loadAll(List)} in one logical retrieval rather than N+1.</p>
 */
public interface PromotionRepository {

    CompletionStage<Promotion> save(Promotion promotion);

    CompletionStage<Optional<Promotion>> findById(PromotionId id);

    CompletionStage<Boolean> existsByName(String name);

    /**
     * Loads the full {@link Promotion} aggregate for each version id.
     *
     * @param versionIds candidate version ids returned by the resolver
     * @return map keyed by version id; missing versions are absent, never
     *         null entries
     */
    CompletionStage<Map<PromotionVersionId, Promotion>> loadAll(List<PromotionVersionId> versionIds);
}
