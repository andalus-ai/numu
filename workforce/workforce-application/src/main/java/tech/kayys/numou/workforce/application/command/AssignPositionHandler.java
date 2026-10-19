package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.workforce.domain.position.PositionAssignment;
import tech.kayys.numou.workforce.domain.position.PositionAssignmentId;
import tech.kayys.numou.workforce.domain.position.policy.PositionAssignmentPolicy;
import tech.kayys.numou.workforce.spi.port.EmploymentRepository;
import tech.kayys.numou.workforce.spi.port.PositionAssignmentRepository;
import tech.kayys.numou.workforce.spi.port.PositionRepository;
import tech.kayys.numou.workforce.spi.port.WorkerRepository;

import java.util.Objects;

public final class AssignPositionHandler
        implements CommandHandler<AssignPositionCommand, Result<PositionAssignmentId>> {

    private final PositionRepository positionRepository;
    private final WorkerRepository workerRepository;
    private final EmploymentRepository employmentRepository;
    private final PositionAssignmentRepository assignmentRepository;
    private final EventPublisher eventPublisher;
    private final PositionAssignmentPolicy policy = new PositionAssignmentPolicy();

    public AssignPositionHandler(
            PositionRepository positionRepository,
            WorkerRepository workerRepository,
            EmploymentRepository employmentRepository,
            PositionAssignmentRepository assignmentRepository,
            EventPublisher eventPublisher) {
        this.positionRepository = Objects.requireNonNull(positionRepository);
        this.workerRepository = Objects.requireNonNull(workerRepository);
        this.employmentRepository = Objects.requireNonNull(employmentRepository);
        this.assignmentRepository = Objects.requireNonNull(assignmentRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<PositionAssignmentId>> handle(AssignPositionCommand command) {
        return Uni.combine().all().unis(
                Uni.createFrom().completionStage(positionRepository.findById(command.positionId())),
                Uni.createFrom().completionStage(workerRepository.findById(command.workerId())),
                Uni.createFrom().completionStage(employmentRepository.findById(command.employmentId()))
        ).asTuple().onItem().transformToUni(tuple -> {
            var optPos = tuple.getItem1();
            var optWorker = tuple.getItem2();
            var optEmployment = tuple.getItem3();

            if (optPos.isEmpty()) {
                return Uni.createFrom().item(Result.failure(ApplicationError.of("POSITION_NOT_FOUND", "Position not found")));
            }
            if (optWorker.isEmpty()) {
                return Uni.createFrom().item(Result.failure(ApplicationError.of("WORKER_NOT_FOUND", "Worker not found")));
            }
            if (optEmployment.isEmpty()) {
                return Uni.createFrom().item(Result.failure(ApplicationError.of("EMPLOYMENT_NOT_FOUND", "Employment not found")));
            }

            var position = optPos.get();
            var worker = optWorker.get();
            var employment = optEmployment.get();

            try {
                policy.validateAssignment(worker, employment, position, command.startDate());
            } catch (Exception e) {
                return Uni.createFrom().item(Result.failure(ApplicationError.of("POLICY_VIOLATION", e.getMessage())));
            }

            var assignmentId = PositionAssignmentId.generate();
            var assignment = PositionAssignment.assign(
                    assignmentId,
                    command.workerId(),
                    command.employmentId(),
                    command.positionId(),
                    command.startDate()
            );

            return Uni.createFrom()
                    .completionStage(assignmentRepository.save(assignment))
                    .onItem()
                    .transformToUni(saved -> eventPublisher
                            .publish(saved.pullDomainEvents())
                            .replaceWith(Result.success(saved.id())));
        });
    }
}
