package tech.kayys.numou.crm.application.opportunity;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.query.OpportunityView;
import tech.kayys.numou.crm.application.api.query.SearchOpportunitiesQuery;
import tech.kayys.numou.crm.domain.identifier.CustomerId;
import tech.kayys.numou.crm.domain.model.Opportunity;
import tech.kayys.numou.crm.domain.repository.OpportunityRepository;
import tech.kayys.numou.foundation.application.query.QueryHandler;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Searches opportunities. Customer / stage drive the repository query;
 * assignedTo and the value range plus paging are applied to the result
 * set (the aggregate exposes no currency-aware range finder).
 */
public final class SearchOpportunitiesQueryHandler
        implements QueryHandler<SearchOpportunitiesQuery, List<OpportunityView>> {

    private final OpportunityRepository opportunityRepository;

    public SearchOpportunitiesQueryHandler(OpportunityRepository opportunityRepository) {
        this.opportunityRepository = Objects.requireNonNull(opportunityRepository, "opportunityRepository cannot be null");
    }

    @Override
    public Uni<List<OpportunityView>> handle(SearchOpportunitiesQuery query) {
        Objects.requireNonNull(query, "query cannot be null");

        return Uni.createFrom().completionStage(find(query))
                .map(opportunities -> opportunities.stream()
                        .filter(opportunity -> matchesAssignedTo(opportunity, query.assignedTo()))
                        .filter(opportunity -> matchesValueRange(opportunity, query.minValue(), query.maxValue()))
                        .skip((long) Math.max(query.page(), 0) * pageSize(query.size()))
                        .limit(pageSize(query.size()))
                        .map(OpportunityView::fromDomain)
                        .collect(Collectors.toList()));
    }

    private CompletionStage<List<Opportunity>> find(SearchOpportunitiesQuery query) {
        if (query.customerId() != null) {
            return opportunityRepository.findByCustomerId(CustomerId.of(query.customerId()));
        }
        if (query.stage() != null) {
            return opportunityRepository.findByStage(query.stage());
        }
        return opportunityRepository.findActiveOpportunities();
    }

    private static boolean matchesAssignedTo(Opportunity opportunity, String assignedTo) {
        if (assignedTo == null || assignedTo.isBlank()) {
            return true;
        }
        return opportunity.getAssignedTo() != null
                && assignedTo.equalsIgnoreCase(opportunity.getAssignedTo().value().toString());
    }

    private static boolean matchesValueRange(Opportunity opportunity, Double minValue, Double maxValue) {
        double value = opportunity.getEstimatedValue();
        if (minValue != null && value < minValue) {
            return false;
        }
        return maxValue == null || value <= maxValue;
    }

    private static int pageSize(int size) {
        return size <= 0 ? 20 : size;
    }
}
