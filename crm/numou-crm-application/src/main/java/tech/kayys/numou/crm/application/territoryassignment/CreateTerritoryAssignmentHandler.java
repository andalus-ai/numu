package tech.kayys.numou.crm.application.territoryassignment;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.command.CreateTerritoryAssignmentCommand;
import tech.kayys.numou.crm.domain.event.territory.TerritoryAssignmentCreated;
import tech.kayys.numou.crm.domain.territory.TerritoryAssignment;
import tech.kayys.numou.crm.domain.repository.TerritoryAssignmentRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;

import java.util.Objects;

/**
 * Handles the creation of a territory assignment.
 */
public final class CreateTerritoryAssignmentHandler
        implements CommandHandler<CreateTerritoryAssignmentCommand, Result<TerritoryAssignment>> {

    private final TerritoryAssignmentRepository territoryAssignmentRepository;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;

    public CreateTerritoryAssignmentHandler(TerritoryAssignmentRepository territoryAssignmentRepository,
                                            EventPublisher eventPublisher,
                                            UnitOfWork unitOfWork) {
        this.territoryAssignmentRepository = Objects.requireNonNull(territoryAssignmentRepository, "territoryAssignmentRepository cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork cannot be null");
    }

    @Override
    public Uni<Result<TerritoryAssignment>> handle(CreateTerritoryAssignmentCommand command) {
        return unitOfWork.execute(() -> {
            final TerritoryAssignment assignment = TerritoryAssignment.create(
                    command.assignmentId(),
                    command.territoryId(),
                    command.accountId(),
                    command.origin(),
                    command.assignedByUserId(),
                    command.assignedAt()
            );
            return Uni.createFrom().completionStage(territoryAssignmentRepository.save(assignment))
                    .flatMap(saved -> eventPublisher.publish(saved.pullDomainEvents())
                            .replaceWith(Result.success(saved)));
        });
    }
}