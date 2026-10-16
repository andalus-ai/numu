package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.compliance.ComplianceEvidence;
import tech.kayys.numou.workforce.domain.compliance.ComplianceEvidenceId;
import tech.kayys.numou.workforce.domain.compliance.ComplianceRequirementId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface ComplianceEvidenceRepository extends Repository<ComplianceEvidence, ComplianceEvidenceId> {
    CompletionStage<List<ComplianceEvidence>> findByWorkerAndRequirement(WorkerId workerId, ComplianceRequirementId requirementId);
    CompletionStage<List<ComplianceEvidence>> findByWorker(WorkerId workerId);
}
