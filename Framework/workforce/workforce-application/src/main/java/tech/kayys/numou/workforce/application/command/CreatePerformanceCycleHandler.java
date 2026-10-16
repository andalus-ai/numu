package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.workforce.domain.performance.PerformanceCycle;
import tech.kayys.numou.workforce.domain.performance.PerformanceCycleId;
import tech.kayys.numou.workforce.spi.port.PerformanceCycleRepository;

import java.util.Objects;

public class CreatePerformanceCycleHandler implements CommandHandler<CreatePerformanceCycleCommand, Result<PerformanceCycleId>> {

    private final PerformanceCycleRepository repository;
    private final EventPublisher eventPublisher;

    public CreatePerformanceCycleHandler(PerformanceCycleRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<PerformanceCycleId>> handle(CreatePerformanceCycleCommand cmd) {
        return Uni.createFrom().completionStage(repository.findByTenantAndCode(cmd.tenantId(), cmd.code()))
                .chain(optExisting -> {
                    if (optExisting.isPresent()) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("CYCLE_EXISTS", "Performance cycle already exists: " + cmd.code())));
                    }
                    PerformanceCycle cycle;
                    try {
                        cycle = PerformanceCycle.create(
                                PerformanceCycleId.generate(),
                                cmd.tenantId(),
                                cmd.code(),
                                cmd.name(),
                                cmd.periodStart(),
                                cmd.periodEnd()
                        );
                    } catch (Exception e) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("INVALID_CYCLE", e.getMessage())));
                    }
                    return Uni.createFrom().completionStage(repository.save(cycle))
                            .chain(saved -> eventPublisher.publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.getId())));
                });
    }
}
