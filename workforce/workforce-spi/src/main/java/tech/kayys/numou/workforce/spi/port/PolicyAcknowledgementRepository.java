package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.compliance.ComplianceRequirementId;
import tech.kayys.numou.workforce.domain.compliance.PolicyAcknowledgement;
import tech.kayys.numou.workforce.domain.compliance.PolicyAcknowledgementId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface PolicyAcknowledgementRepository extends Repository<PolicyAcknowledgement, PolicyAcknowledgementId> {
    CompletionStage<Optional<PolicyAcknowledgement>> findByWorkerAndRequirement(WorkerId workerId, ComplianceRequirementId requirementId);
    CompletionStage<List<PolicyAcknowledgement>> findByWorker(WorkerId workerId);
}
