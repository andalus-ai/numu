package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.workforce.domain.benefit.WorkerBenefitEnrollment;
import tech.kayys.numou.workforce.domain.benefit.WorkerBenefitEnrollmentId;
import tech.kayys.numou.workforce.spi.port.BenefitRepository;
import tech.kayys.numou.workforce.spi.port.WorkerBenefitEnrollmentRepository;
import tech.kayys.numou.workforce.spi.port.WorkerRepository;

import java.util.Objects;

public class EnrollWorkerBenefitHandler implements CommandHandler<EnrollWorkerBenefitCommand, Result<WorkerBenefitEnrollmentId>> {

    private final WorkerRepository workerRepository;
    private final BenefitRepository benefitRepository;
    private final WorkerBenefitEnrollmentRepository enrollmentRepository;
    private final EventPublisher eventPublisher;

    public EnrollWorkerBenefitHandler(
            WorkerRepository workerRepository,
            BenefitRepository benefitRepository,
            WorkerBenefitEnrollmentRepository enrollmentRepository,
            EventPublisher eventPublisher
    ) {
        this.workerRepository = Objects.requireNonNull(workerRepository);
        this.benefitRepository = Objects.requireNonNull(benefitRepository);
        this.enrollmentRepository = Objects.requireNonNull(enrollmentRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<WorkerBenefitEnrollmentId>> handle(EnrollWorkerBenefitCommand cmd) {
        return Uni.createFrom().completionStage(workerRepository.findById(cmd.workerId()))
                .chain(optWorker -> {
                    if (optWorker.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("WORKER_NOT_FOUND", "Worker not found")));
                    }
                    return Uni.createFrom().completionStage(benefitRepository.findById(cmd.benefitId()))
                            .chain(optBenefit -> {
                                if (optBenefit.isEmpty()) {
                                    return Uni.createFrom().item(Result.failure(ApplicationError.of("BENEFIT_NOT_FOUND", "Benefit not found")));
                                }
                                if (!optBenefit.get().isActive()) {
                                    return Uni.createFrom().item(Result.failure(ApplicationError.of("BENEFIT_NOT_ACTIVE", "Benefit is inactive")));
                                }
                                WorkerBenefitEnrollment enrollment = WorkerBenefitEnrollment.create(
                                        WorkerBenefitEnrollmentId.generate(),
                                        cmd.workerId(),
                                        cmd.employmentId(),
                                        cmd.benefitId(),
                                        cmd.effectiveFrom()
                                );
                                return Uni.createFrom().completionStage(enrollmentRepository.save(enrollment))
                                        .chain(saved -> eventPublisher.publish(saved.pullDomainEvents())
                                                .replaceWith(Result.success(saved.getId())));
                            });
                });
    }
}
