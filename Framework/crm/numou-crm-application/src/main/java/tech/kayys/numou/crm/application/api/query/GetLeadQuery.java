package tech.kayys.numou.crm.application.api.query;

import tech.kayys.numou.foundation.application.query.Query;
import tech.kayys.numou.crm.domain.identifier.LeadId;

/**
 * Query to get a lead by ID.
 */
public record GetLeadQuery(LeadId leadId) implements Query {

    public GetLeadQuery {
        if (leadId == null) {
            throw new IllegalArgumentException("Lead ID cannot be null");
        }
    }
}