package tech.kayys.numou.crm.application.api.query;

import tech.kayys.numou.crm.domain.identifier.OpportunityId;
import tech.kayys.numou.foundation.application.query.Query;

public record GetOpportunityQuery(OpportunityId opportunityId) implements Query {}
