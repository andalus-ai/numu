package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.domain.ref.PersonRef;
import tech.kayys.numou.workforce.domain.worker.Worker;
import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.spi.port.WorkerRepository;

import java.time.Instant;
import java.util.Objects;

public final class RegisterWorkerHandler
        implements CommandHandler<RegisterWorkerCommand, Result<WorkerId>> {

    private final WorkerRepository workerRepository;
    private final EventPublisher eventPublisher;

    public RegisterWorkerHandler(WorkerRepository workerRepository, EventPublisher eventPublisher) {
        this.workerRepository = Objects.requireNonNull(workerRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<WorkerId>> handle(RegisterWorkerCommand command) {
        var personRef = PersonRef.of(command.personId());

        return Uni.createFrom()
                .completionStage(workerRepository.findByPersonRef(command.tenantId(), personRef))
                .onItem()
                .transformToUni(existing -> {
                    if (existing.isPresent()) {
                        return Uni.createFrom().item(
                                Result.failure(
                                        ApplicationError.of(
                                                "WORKER_ALREADY_EXISTS",
                                                "A worker already exists for person: " + command.personId()
                                        )
                                )
                        );
                    }

                    var workerId = WorkerId.generate();
                    var actor = command.actor() != null ? command.actor() : "system";

                    var worker = Worker.create(
                            workerId,
                            command.tenantId(),
                            personRef,
                            command.workerType(),
                            actor,
                            Instant.now()
                    );

                    return Uni.createFrom()
                            .completionStage(workerRepository.save(worker))
                            .onItem()
                            .transformToUni(saved -> eventPublisher
                                    .publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.id())));
                });
    }
}
