package tech.kayys.numou.crm.application.lead;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.query.GetLeadQuery;
import tech.kayys.numou.crm.application.api.query.LeadView;
import tech.kayys.numou.crm.domain.repository.LeadRepository;
import tech.kayys.numou.foundation.application.query.QueryHandler;

import java.util.Objects;

/**
 * Reads a single lead and projects it to {@link LeadView}. A missing
 * lead surfaces as {@link IllegalArgumentException}, which the REST
 * adapter maps to 404.
 */
public final class GetLeadQueryHandler implements QueryHandler<GetLeadQuery, LeadView> {

    private final LeadRepository leadRepository;

    public GetLeadQueryHandler(LeadRepository leadRepository) {
        this.leadRepository = Objects.requireNonNull(leadRepository, "leadRepository cannot be null");
    }

    @Override
    public Uni<LeadView> handle(GetLeadQuery query) {
        Objects.requireNonNull(query, "query cannot be null");

        return Uni.createFrom().completionStage(leadRepository.findById(query.leadId()))
                .map(optional -> optional.orElseThrow(
                        () -> new IllegalArgumentException("Lead not found: " + query.leadId())))
                .map(LeadView::fromDomain);
    }
}
