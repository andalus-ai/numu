package tech.kayys.syirkah.commerce.promotion.domain;

import java.util.List;
import java.util.Objects;

/**
 * Default promotion resolver (product04.md section 16).
 *
 * <p>The resolver is intentionally narrow: it delegates candidate discovery
 * to the {@link PromotionCandidateRepository} and performs no business
 * eligibility. The complexity belongs behind the repository/index
 * implementation, so this class stays almost trivial by design.</p>
 */
public final class DefaultPromotionResolver implements PromotionResolver {

    private final PromotionCandidateRepository repository;

    public DefaultPromotionResolver(PromotionCandidateRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    @Override
    public List<PromotionCandidate> resolve(PromotionResolveRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        return repository.findCandidates(request);
    }
}
