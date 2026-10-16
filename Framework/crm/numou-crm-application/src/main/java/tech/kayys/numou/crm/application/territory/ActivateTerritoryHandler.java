package tech.kayys.numou.crm.application.territory;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.command.ActivateTerritoryCommand;
import tech.kayys.numou.crm.domain.event.territory.TerritoryActivated;
import tech.kayys.numou.crm.domain.territory.Territory;
import tech.kayys.numou.crm.domain.repository.TerritoryRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;

import java.util.Objects;
import java.util.Optional;

/**
 * Handles activating a territory.
 */
public final class ActivateTerritoryHandler
        implements CommandHandler<ActivateTerritoryCommand, Result<Territory>> {

    private final TerritoryRepository territoryRepository;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;

    public ActivateTerritoryHandler(TerritoryRepository territoryRepository,
                                    EventPublisher eventPublisher,
                                    UnitOfWork unitOfWork) {
        this.territoryRepository = Objects.requireNonNull(territoryRepository, "territoryRepository cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork cannot be null");
    }

    @Override
    public Uni<Result<Territory>> handle(ActivateTerritoryCommand command) {
        return unitOfWork.execute(() -> Uni.createFrom().completionStage(
                        territoryRepository.findById(command.territoryId()))
                .flatMap(optTerritory -> {
                    Territory territory = optTerritory.orElse(null);
                    if (territory == null) {
                        return Uni.createFrom().item(Result.failure(
                                ApplicationError.of("TERRITORY_NOT_FOUND", "Territory not found with id: " + command.territoryId())));
                    }
                    if (territory.isActive()) {
                        return Uni.createFrom().item(Result.success(territory));
                    }
                    territory.activate();
                    return Uni.createFrom().completionStage(territoryRepository.save(territory))
                            .flatMap(saved -> eventPublisher.publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved)));
                }));
    }
}