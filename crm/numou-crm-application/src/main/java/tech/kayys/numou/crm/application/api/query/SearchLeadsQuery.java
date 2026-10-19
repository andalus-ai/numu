package tech.kayys.numou.crm.application.api.query;

import tech.kayys.numou.crm.domain.valueobject.LeadStatus;
import tech.kayys.numou.foundation.application.query.Query;

public record SearchLeadsQuery(
        LeadStatus status,
        String email,
        String company,
        String source,
        Double minScore,
        int page,
        int size
) implements Query {}
