package tech.kayys.syirkah.project.application.commercial.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.application.commercial.CommercialErrors;
import tech.kayys.syirkah.project.application.commercial.command.ApplyAdvanceCommand;
import tech.kayys.syirkah.project.domain.commercial.ProjectAdvanceId;
import tech.kayys.syirkah.project.spi.port.ProjectAdvanceRepository;

import java.util.Objects;

public final class ApplyAdvanceHandler
        implements CommandHandler<ApplyAdvanceCommand, Result<ProjectAdvanceId>> {

    private final ProjectAdvanceRepository repository;
    private final EventPublisher eventPublisher;

    public ApplyAdvanceHandler(
            ProjectAdvanceRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProjectAdvanceId>> handle(ApplyAdvanceCommand command) {
        return Uni.createFrom()
                .completionStage(repository.findById(command.advanceId()))
                .onItem()
                .transformToUni(advanceOpt -> {
                    if (advanceOpt.isEmpty()) {
                        return Uni.createFrom().item(
                                Result.failure(
                                        ApplicationError.of(
                                                CommercialErrors.ADVANCE_NOT_FOUND,
                                                "Advance not found: " + command.advanceId().value()
                                        )
                                )
                        );
                    }

                    var advance = advanceOpt.get();
                    try {
                        advance.apply(command.amount());
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
                            .completionStage(repository.save(advance))
                            .onItem()
                            .transformToUni(saved -> eventPublisher
                                    .publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.id())));
                });
    }
}
