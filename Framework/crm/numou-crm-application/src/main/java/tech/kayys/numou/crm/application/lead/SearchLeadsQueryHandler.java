package tech.kayys.numou.crm.application.lead;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.query.LeadView;
import tech.kayys.numou.crm.application.api.query.SearchLeadsQuery;
import tech.kayys.numou.crm.domain.model.Lead;
import tech.kayys.numou.crm.domain.repository.LeadRepository;
import tech.kayys.numou.foundation.application.query.QueryHandler;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Searches leads. The repository exposes single-criterion finders, so
 * the most selective supplied criterion drives the query and any
 * remaining criteria (company, source) plus paging are applied to the
 * result set.
 */
public final class SearchLeadsQueryHandler implements QueryHandler<SearchLeadsQuery, List<LeadView>> {

    private final LeadRepository leadRepository;

    public SearchLeadsQueryHandler(LeadRepository leadRepository) {
        this.leadRepository = Objects.requireNonNull(leadRepository, "leadRepository cannot be null");
    }

    @Override
    public Uni<List<LeadView>> handle(SearchLeadsQuery query) {
        Objects.requireNonNull(query, "query cannot be null");

        return Uni.createFrom().completionStage(find(query))
                .map(leads -> leads.stream()
                        .filter(lead -> matchesCompany(lead, query.company()))
                        .filter(lead -> matchesSource(lead, query.source()))
                        .skip((long) Math.max(query.page(), 0) * pageSize(query.size()))
                        .limit(pageSize(query.size()))
                        .map(LeadView::fromDomain)
                        .collect(Collectors.toList()));
    }

    private CompletionStage<List<Lead>> find(SearchLeadsQuery query) {
        if (query.status() != null) {
            return leadRepository.findByStatus(query.status());
        }
        if (isPresent(query.email())) {
            return leadRepository.findByEmail(query.email());
        }
        if (query.minScore() != null) {
            return leadRepository.findByScoreGreaterThan(query.minScore());
        }
        return leadRepository.findActiveLeads();
    }

    private static boolean matchesCompany(Lead lead, String company) {
        return !isPresent(company)
                || (lead.getCompany() != null && lead.getCompany().equalsIgnoreCase(company));
    }

    private static boolean matchesSource(Lead lead, String source) {
        return !isPresent(source) || source.equalsIgnoreCase(lead.getSource());
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static int pageSize(int size) {
        return size <= 0 ? 20 : size;
    }
}
