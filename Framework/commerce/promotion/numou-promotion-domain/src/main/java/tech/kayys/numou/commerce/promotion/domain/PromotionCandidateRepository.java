package tech.kayys.syirkah.commerce.promotion.domain;

import java.util.List;

/**
 * Candidate discovery boundary (product04.md section 5).
 *
 * <p>The domain/application layer should not know how candidates are stored.
 * Infrastructure can implement this with PostgreSQL, JPA, SQL, Elasticsearch,
 * Redis, a materialized index, etc. without changing the promotion engine.
 *
 * <p>The repository returns versioned {@link PromotionCandidate} ids, NOT
 * full aggregates. Bulk loading the definitions is a separate step behind the
 * {@link PromotionRepository} port, so discovery stays storage-agnostic
 * (product04.md section 28).</p>
 */
public interface PromotionCandidateRepository {

    List<PromotionCandidate> findCandidates(PromotionResolveRequest request);
}
