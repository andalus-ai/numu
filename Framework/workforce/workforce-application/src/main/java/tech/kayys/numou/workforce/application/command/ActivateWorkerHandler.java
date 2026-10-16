package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.spi.port.WorkerRepository;

import java.time.Instant;
import java.util.Objects;

public final class ActivateWorkerHandler
        implements CommandHandler<ActivateWorkerCommand, Result<WorkerId>> {

    private final WorkerRepository workerRepository;
    private final EventPublisher eventPublisher;

    public ActivateWorkerHandler(WorkerRepository workerRepository, EventPublisher eventPublisher) {
        this.workerRepository = Objects.requireNonNull(workerRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<WorkerId>> handle(ActivateWorkerCommand command) {
        return Uni.createFrom()
                .completionStage(workerRepository.findById(command.workerId()))
                .onItem()
                .transformToUni(opt -> {
                    if (opt.isEmpty()) {
                        return Uni.createFrom().item(
                                Result.failure(
                                        ApplicationError.of(
                                                "WORKER_NOT_FOUND",
                                                "Worker not found: " + command.workerId().value()
                                        )
                                )
                        );
                    }

                    var worker = opt.get();
                    var actor = command.actor() != null ? command.actor() : "system";
                    try {
                        worker.activate(actor, Instant.now());
                    } catch (Exception e) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("ACTIVATION_FAILED", e.getMessage())));
                    }

                    return Uni.createFrom()
                            .completionStage(workerRepository.save(worker))
                            .onItem()
                            .transformToUni(saved -> eventPublisher
                                    .publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.id())));
                });
    }
}
