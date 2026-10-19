package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.socialinsurance.WorkerSocialInsuranceEnrollment;
import tech.kayys.numou.workforce.domain.socialinsurance.WorkerSocialInsuranceEnrollmentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface WorkerSocialInsuranceEnrollmentRepository extends Repository<WorkerSocialInsuranceEnrollment, WorkerSocialInsuranceEnrollmentId> {

    CompletionStage<List<WorkerSocialInsuranceEnrollment>> findByWorker(WorkerId workerId);

    CompletionStage<List<WorkerSocialInsuranceEnrollment>> findByEmployment(EmploymentId employmentId);
}
