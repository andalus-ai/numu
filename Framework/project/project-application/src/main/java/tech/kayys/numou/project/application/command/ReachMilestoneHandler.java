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
 * Marks a milestone as reached and publishes MilestoneReached.
 *
 * Only PLANNED milestones can be reached - the aggregate enforces it.
 */
public final class ReachMilestoneHandler
        implements CommandHandler<ReachMilestoneCommand, Result<ProjectMilestoneId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of("MILESTONE_NOT_FOUND", "Project milestone does not exist");

    private final ProjectMilestoneRepository milestones;
    private final EventPublisher eventPublisher;

    public ReachMilestoneHandler(
            ProjectMilestoneRepository milestones,
            EventPublisher eventPublisher
    ) {
        this.milestones = Objects.requireNonNull(milestones);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProjectMilestoneId>> handle(ReachMilestoneCommand command) {
        return Uni.createFrom()
                .completionStage(milestones.findById(command.milestoneId()))
                .onItem()
                .transformToUni(maybeMilestone -> reach(maybeMilestone, command));
    }

    private Uni<Result<ProjectMilestoneId>> reach(
            Optional<ProjectMilestone> maybeMilestone,
            ReachMilestoneCommand command
    ) {
        if (maybeMilestone.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        var milestone = maybeMilestone.get();
        milestone.reach(command.actualDate());

        return Uni.createFrom()
                .completionStage(milestones.save(milestone))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
