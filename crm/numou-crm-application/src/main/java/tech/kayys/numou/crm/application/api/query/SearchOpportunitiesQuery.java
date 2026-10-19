package tech.kayys.numou.crm.application.api.query;

import tech.kayys.numou.crm.domain.valueobject.OpportunityStage;
import tech.kayys.numou.foundation.application.query.Query;

import java.util.UUID;

public record SearchOpportunitiesQuery(
        UUID customerId,
        OpportunityStage stage,
        String assignedTo,
        Double minValue,
        Double maxValue,
        int page,
        int size
) implements Query {}
