package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.benefit.WorkerBenefitEnrollment;
import tech.kayys.numou.workforce.domain.benefit.WorkerBenefitEnrollmentId;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletionStage;

public interface WorkerBenefitEnrollmentRepository extends Repository<WorkerBenefitEnrollment, WorkerBenefitEnrollmentId> {

    CompletionStage<List<WorkerBenefitEnrollment>> findByWorker(WorkerId workerId);

    CompletionStage<List<WorkerBenefitEnrollment>> findByEmployment(EmploymentId employmentId);

    CompletionStage<List<WorkerBenefitEnrollment>> findByWorkerAndEffectiveDate(WorkerId workerId, LocalDate date);
}
