package tech.kayys.numou.project.application.commercial.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.project.application.commercial.CommercialErrors;
import tech.kayys.numou.project.application.commercial.command.ReleaseRetentionCommand;
import tech.kayys.numou.project.domain.commercial.ProjectRetentionId;
import tech.kayys.numou.project.spi.port.ProjectRetentionRepository;

import java.util.Objects;

public final class ReleaseRetentionHandler
        implements CommandHandler<ReleaseRetentionCommand, Result<ProjectRetentionId>> {

    private final ProjectRetentionRepository repository;
    private final EventPublisher eventPublisher;

    public ReleaseRetentionHandler(
            ProjectRetentionRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProjectRetentionId>> handle(ReleaseRetentionCommand command) {
        return Uni.createFrom()
                .completionStage(repository.findById(command.retentionId()))
                .onItem()
                .transformToUni(retentionOpt -> {
                    if (retentionOpt.isEmpty()) {
                        return Uni.createFrom().item(
                                Result.failure(
                                        ApplicationError.of(
                                                CommercialErrors.RETENTION_NOT_FOUND,
                                                "Retention not found: " + command.retentionId().value()
                                        )
                                )
                        );
                    }

                    var retention = retentionOpt.get();
                    try {
                        retention.release(command.amount());
                    } catch (RuntimeException ex) {
                        return Uni.createFrom().item(
                                Result.failure(
                                        ApplicationError.of(
                                                CommercialErrors.INVALID_COMMERCIAL_STATE,
                                                ex.getMessage()
                                        )
                                )
                        );
                    }

                    return Uni.createFrom()
                            .completionStage(repository.save(retention))
                            .onItem()
                            .transformToUni(saved -> eventPublisher
                                    .publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.id())));
                });
    }
}
