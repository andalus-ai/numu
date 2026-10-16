package tech.kayys.numou.crm.domain.repository;

import tech.kayys.numou.crm.domain.identifier.LeadId;
import tech.kayys.numou.crm.domain.model.Lead;
import tech.kayys.numou.crm.domain.valueobject.LeadStatus;
import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.identity.domain.user.UserId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface LeadRepository extends Repository<Lead, LeadId> {
    CompletionStage<List<Lead>> findByStatus(LeadStatus status);
    CompletionStage<List<Lead>> findByEmail(String email);
    CompletionStage<List<Lead>> findByAssignedTo(UserId assignedTo);
    CompletionStage<List<Lead>> findActiveLeads();
    CompletionStage<List<Lead>> findQualifiedLeads();
    CompletionStage<List<Lead>> findByScoreGreaterThan(double score);
    CompletionStage<Long> countByStatus(LeadStatus status);
}
