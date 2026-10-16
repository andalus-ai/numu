package tech.kayys.numou.crm.application.territoryassignment;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.command.EndTerritoryAssignmentCommand;
import tech.kayys.numou.crm.domain.event.territory.TerritoryAssignmentEnded;
import tech.kayys.numou.crm.domain.territory.TerritoryAssignment;
import tech.kayys.numou.crm.domain.repository.TerritoryAssignmentRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;

import java.util.Objects;
import java.util.Optional;

/**
 * Handles ending a territory assignment.
 */
public final class EndTerritoryAssignmentHandler
        implements CommandHandler<EndTerritoryAssignmentCommand, Result<TerritoryAssignment>> {

    private final TerritoryAssignmentRepository territoryAssignmentRepository;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;

    public EndTerritoryAssignmentHandler(TerritoryAssignmentRepository territoryAssignmentRepository,
                                         EventPublisher eventPublisher,
                                         UnitOfWork unitOfWork) {
        this.territoryAssignmentRepository = Objects.requireNonNull(territoryAssignmentRepository, "territoryAssignmentRepository cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork cannot be null");
    }

    @Override
    public Uni<Result<TerritoryAssignment>> handle(EndTerritoryAssignmentCommand command) {
        return unitOfWork.execute(() -> Uni.createFrom().completionStage(
                        territoryAssignmentRepository.findById(command.assignmentId()))
                .flatMap(optAssignment -> {
                    TerritoryAssignment assignment = optAssignment.orElse(null);
                    if (assignment == null) {
                        return Uni.createFrom().item(Result.failure(
                                ApplicationError.of("ASSIGNMENT_NOT_FOUND", "Territory assignment not found with id: " + command.assignmentId())));
                    }
                    if (!assignment.isActive()) {
                        return Uni.createFrom().item(Result.success(assignment));
                    }
                    assignment.end(command.endedAt());
                    return Uni.createFrom().completionStage(territoryAssignmentRepository.save(assignment))
                            .flatMap(saved -> eventPublisher.publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved)));
                }));
    }
}