package tech.kayys.numou.crm.application.opportunity;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.query.GetOpportunityQuery;
import tech.kayys.numou.crm.application.api.query.OpportunityView;
import tech.kayys.numou.crm.domain.repository.OpportunityRepository;
import tech.kayys.numou.foundation.application.query.QueryHandler;

import java.util.Objects;

/**
 * Reads a single opportunity and projects it to {@link OpportunityView}.
 * A missing opportunity surfaces as {@link IllegalArgumentException} (404).
 */
public final class GetOpportunityQueryHandler implements QueryHandler<GetOpportunityQuery, OpportunityView> {

    private final OpportunityRepository opportunityRepository;

    public GetOpportunityQueryHandler(OpportunityRepository opportunityRepository) {
        this.opportunityRepository = Objects.requireNonNull(opportunityRepository, "opportunityRepository cannot be null");
    }

    @Override
    public Uni<OpportunityView> handle(GetOpportunityQuery query) {
        Objects.requireNonNull(query, "query cannot be null");

        return Uni.createFrom().completionStage(opportunityRepository.findById(query.opportunityId()))
                .map(optional -> optional.orElseThrow(
                        () -> new IllegalArgumentException("Opportunity not found: " + query.opportunityId())))
                .map(OpportunityView::fromDomain);
    }
}
