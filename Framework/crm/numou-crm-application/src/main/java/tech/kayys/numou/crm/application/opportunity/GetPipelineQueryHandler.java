package tech.kayys.numou.crm.application.opportunity;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.query.GetPipelineQuery;
import tech.kayys.numou.crm.application.api.query.PipelineView;
import tech.kayys.numou.crm.domain.identifier.CustomerId;
import tech.kayys.numou.crm.domain.model.Opportunity;
import tech.kayys.numou.crm.domain.repository.OpportunityRepository;
import tech.kayys.numou.crm.domain.valueobject.OpportunityStage;
import tech.kayys.numou.foundation.application.query.QueryHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Builds the sales {@link PipelineView}: opportunities grouped by stage
 * with per-stage and overall totals. When a customer id is supplied the
 * pipeline is scoped to that customer, then optionally narrowed to a
 * single assignee.
 */
public final class GetPipelineQueryHandler implements QueryHandler<GetPipelineQuery, PipelineView> {

    private final OpportunityRepository opportunityRepository;

    public GetPipelineQueryHandler(OpportunityRepository opportunityRepository) {
        this.opportunityRepository = Objects.requireNonNull(opportunityRepository, "opportunityRepository cannot be null");
    }

    @Override
    public Uni<PipelineView> handle(GetPipelineQuery query) {
        Objects.requireNonNull(query, "query cannot be null");

        return Uni.createFrom().completionStage(find(query))
                .map(opportunities -> project(filterByAssignee(opportunities, query.assignedTo())));
    }

    private CompletionStage<List<Opportunity>> find(GetPipelineQuery query) {
        if (query.customerId() != null) {
            return opportunityRepository.findByCustomerId(CustomerId.of(query.customerId()));
        }
        return opportunityRepository.findActiveOpportunities();
    }

    private static List<Opportunity> filterByAssignee(List<Opportunity> opportunities, String assignedTo) {
        if (assignedTo == null || assignedTo.isBlank()) {
            return opportunities;
        }
        return opportunities.stream()
                .filter(opportunity -> opportunity.getAssignedTo() != null
                        && assignedTo.equalsIgnoreCase(opportunity.getAssignedTo().value().toString()))
                .collect(Collectors.toList());
    }

    private static PipelineView project(List<Opportunity> opportunities) {
        List<PipelineView.PipelineStageView> stages = new ArrayList<>();
        for (OpportunityStage stage : OpportunityStage.values()) {
            List<Opportunity> inStage = opportunities.stream()
                    .filter(opportunity -> opportunity.getStage() == stage)
                    .collect(Collectors.toList());

            stages.add(new PipelineView.PipelineStageView(
                    stage,
                    stage.name(),
                    stage.getDescription(),
                    inStage.size(),
                    inStage.stream().mapToDouble(Opportunity::getEstimatedValue).sum(),
                    inStage.stream().mapToDouble(Opportunity::getWeightedValue).sum(),
                    inStage.stream().map(GetPipelineQueryHandler::toSummary).collect(Collectors.toList())));
        }

        int won = (int) opportunities.stream().filter(o -> o.getStage() == OpportunityStage.WON).count();
        int lost = (int) opportunities.stream().filter(o -> o.getStage() == OpportunityStage.LOST).count();
        int active = (int) opportunities.stream().filter(Opportunity::isActive).count();

        return new PipelineView(
                stages,
                opportunities.stream().mapToDouble(Opportunity::getEstimatedValue).sum(),
                opportunities.stream().mapToDouble(Opportunity::getWeightedValue).sum(),
                opportunities.size(),
                won,
                lost,
                active);
    }

    private static PipelineView.OpportunitySummaryView toSummary(Opportunity opportunity) {
        return new PipelineView.OpportunitySummaryView(
                opportunity.getId().toString(),
                opportunity.getName(),
                opportunity.getCustomerName(),
                opportunity.getEstimatedValue(),
                opportunity.getProbability(),
                opportunity.getWeightedValue(),
                opportunity.getAssignedTo() != null ? opportunity.getAssignedTo().value().toString() : null,
                opportunity.getExpectedCloseDate() != null ? opportunity.getExpectedCloseDate().toString() : null);
    }
}
