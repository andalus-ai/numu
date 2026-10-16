package tech.kayys.syirkah.project.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.domain.milestone.ProjectMilestone;
import tech.kayys.syirkah.project.domain.milestone.ProjectMilestoneId;
import tech.kayys.syirkah.project.spi.port.ProjectMilestoneRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * Cancels a milestone and publishes MilestoneCancelled.
 *
 * A reached milestone can never be cancelled - the aggregate enforces
 * it so a contractually achieved milestone cannot silently disappear.
 */
public final class CancelMilestoneHandler
        implements CommandHandler<CancelMilestoneCommand, Result<ProjectMilestoneId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of("MILESTONE_NOT_FOUND", "Project milestone does not exist");

    private final ProjectMilestoneRepository milestones;
    private final EventPublisher eventPublisher;

    public CancelMilestoneHandler(
            ProjectMilestoneRepository milestones,
            EventPublisher eventPublisher
    ) {
        this.milestones = Objects.requireNonNull(milestones);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProjectMilestoneId>> handle(CancelMilestoneCommand command) {
        return Uni.createFrom()
                .completionStage(milestones.findById(command.milestoneId()))
                .onItem()
                .transformToUni(this::cancelAndPublish);
    }

    private Uni<Result<ProjectMilestoneId>> cancelAndPublish(
            Optional<ProjectMilestone> maybeMilestone
    ) {
        if (maybeMilestone.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        var milestone = maybeMilestone.get();
        milestone.cancel();

        return Uni.createFrom()
                .completionStage(milestones.save(milestone))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
