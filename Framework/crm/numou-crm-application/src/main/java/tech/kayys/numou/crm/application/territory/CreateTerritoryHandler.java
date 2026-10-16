package tech.kayys.numou.crm.application.territory;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.command.CreateTerritoryCommand;
import tech.kayys.numou.crm.domain.event.territory.TerritoryCreated;
import tech.kayys.numou.crm.domain.territory.Territory;
import tech.kayys.numou.crm.domain.repository.TerritoryRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;

import java.util.Objects;

/**
 * Handles the creation of a territory.
 */
public final class CreateTerritoryHandler
        implements CommandHandler<CreateTerritoryCommand, Result<Territory>> {

    private final TerritoryRepository territoryRepository;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;

    public CreateTerritoryHandler(TerritoryRepository territoryRepository,
                                  EventPublisher eventPublisher,
                                  UnitOfWork unitOfWork) {
        this.territoryRepository = Objects.requireNonNull(territoryRepository, "territoryRepository cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork cannot be null");
    }

    @Override
    public Uni<Result<Territory>> handle(CreateTerritoryCommand command) {
        return unitOfWork.execute(() -> {
            final Territory territory = Territory.create(
                    command.territoryId(),
                    command.name(),
                    command.description(),
                    command.parentTerritoryId()
            );
            return Uni.createFrom().completionStage(territoryRepository.save(territory))
                    .flatMap(saved -> eventPublisher.publish(saved.pullDomainEvents())
                            .replaceWith(Result.success(saved)));
        });
    }
}