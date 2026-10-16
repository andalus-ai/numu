package tech.kayys.syirkah.project.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.domain.phase.ProjectPhase;
import tech.kayys.syirkah.project.domain.phase.ProjectPhaseId;
import tech.kayys.syirkah.project.spi.port.ProjectPhaseRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * Completes an ACTIVE phase.
 *
 * Illegal phase transitions are rejected by the aggregate itself
 * (InvalidPhaseStateException).
 */
public final class CompletePhaseHandler
        implements CommandHandler<CompletePhaseCommand, Result<ProjectPhaseId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of("PHASE_NOT_FOUND", "Project phase does not exist");

    private final ProjectPhaseRepository phases;
    private final EventPublisher eventPublisher;

    public CompletePhaseHandler(
            ProjectPhaseRepository phases,
            EventPublisher eventPublisher
    ) {
        this.phases = Objects.requireNonNull(phases);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProjectPhaseId>> handle(CompletePhaseCommand command) {
        return Uni.createFrom()
                .completionStage(phases.findById(command.phaseId()))
                .onItem()
                .transformToUni(this::applyAndPublish);
    }

    private Uni<Result<ProjectPhaseId>> applyAndPublish(Optional<ProjectPhase> maybePhase) {
        if (maybePhase.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        var phase = maybePhase.get();
        phase.complete();

        return Uni.createFrom()
                .completionStage(phases.save(phase))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
