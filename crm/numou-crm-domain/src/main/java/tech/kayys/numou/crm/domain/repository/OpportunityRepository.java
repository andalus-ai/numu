package tech.kayys.numou.crm.domain.repository;

import tech.kayys.numou.crm.domain.identifier.CustomerId;
import tech.kayys.numou.crm.domain.identifier.OpportunityId;
import tech.kayys.numou.crm.domain.model.Opportunity;
import tech.kayys.numou.crm.domain.valueobject.OpportunityStage;
import tech.kayys.numou.foundation.domain.repository.Repository;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface OpportunityRepository extends Repository<Opportunity, OpportunityId> {
    CompletionStage<List<Opportunity>> findByCustomerId(CustomerId customerId);
    CompletionStage<List<Opportunity>> findByStage(OpportunityStage stage);
    CompletionStage<List<Opportunity>> findActiveOpportunities();
}
