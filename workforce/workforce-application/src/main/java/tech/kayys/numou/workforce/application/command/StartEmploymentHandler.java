package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.workforce.domain.employment.Employment;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.employment.OrganizationRef;
import tech.kayys.numou.workforce.domain.employment.PositionRef;
import tech.kayys.numou.workforce.spi.port.EmploymentRepository;
import tech.kayys.numou.workforce.spi.port.WorkerRepository;

import java.util.Objects;

public final class StartEmploymentHandler
        implements CommandHandler<StartEmploymentCommand, Result<EmploymentId>> {

    private final EmploymentRepository employmentRepository;
    private final WorkerRepository workerRepository;
    private final EventPublisher eventPublisher;

    public StartEmploymentHandler(
            EmploymentRepository employmentRepository,
            WorkerRepository workerRepository,
            EventPublisher eventPublisher) {
        this.employmentRepository = Objects.requireNonNull(employmentRepository);
        this.workerRepository = Objects.requireNonNull(workerRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<EmploymentId>> handle(StartEmploymentCommand command) {
        return Uni.createFrom()
                .completionStage(workerRepository.findById(command.workerId()))
                .onItem()
                .transformToUni(optWorker -> {
                    if (optWorker.isEmpty()) {
                        return Uni.createFrom().item(
                                Result.failure(
                                        ApplicationError.of(
                                                "WORKER_NOT_FOUND",
                                                "Worker not found: " + command.workerId().value()
                                        )
                                )
                        );
                    }

                    var worker = optWorker.get();
                    if (!worker.status().isActive()) {
                        return Uni.createFrom().item(
                                Result.failure(
                                        ApplicationError.of(
                                                "WORKER_NOT_ACTIVE",
                                                "Worker is not in active status: " + worker.status()
                                        )
                                )
                        );
                    }

                    var employmentId = EmploymentId.generate();
                    var orgRef = OrganizationRef.of(command.organizationId());
                    var posRef = command.positionId() != null ? PositionRef.of(command.positionId()) : null;

                    var employment = Employment.start(
                            employmentId,
                            command.workerId(),
                            orgRef,
                            posRef,
                            command.type(),
                            command.startDate()
                    );

                    return Uni.createFrom()
                            .completionStage(employmentRepository.save(employment))
                            .onItem()
                            .transformToUni(saved -> eventPublisher
                                    .publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.id())));
                });
    }
}
