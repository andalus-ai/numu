package tech.kayys.numou.crm.application.territory;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.command.UpdateTerritoryCommand;
import tech.kayys.numou.crm.domain.event.territory.TerritoryDefinitionChanged;
import tech.kayys.numou.crm.domain.event.territory.TerritoryRenamed;
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
 * Handles updating a territory's name or description.
 */
public final class UpdateTerritoryHandler
        implements CommandHandler<UpdateTerritoryCommand, Result<Territory>> {

    private final TerritoryRepository territoryRepository;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;

    public UpdateTerritoryHandler(TerritoryRepository territoryRepository,
                                  EventPublisher eventPublisher,
                                  UnitOfWork unitOfWork) {
        this.territoryRepository = Objects.requireNonNull(territoryRepository, "territoryRepository cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork cannot be null");
    }

    @Override
    public Uni<Result<Territory>> handle(UpdateTerritoryCommand command) {
        return unitOfWork.execute(() -> Uni.createFrom().completionStage(
                        territoryRepository.findById(command.territoryId()))
                .flatMap(optTerritory -> {
                    Territory territory = optTerritory.orElse(null);
                    if (territory == null) {
                        return Uni.createFrom().item(Result.failure(
                                ApplicationError.of("TERRITORY_NOT_FOUND", "Territory not found with id: " + command.territoryId())));
                    }
                    boolean changed = false;
                    if (command.name() != null && !command.name().isBlank()) {
                        if (!Objects.equals(territory.name(), command.name())) {
                            territory.rename(command.name());
                            changed = true;
                        }
                    }
                    if (command.description() != null) {
                        if (!Objects.equals(territory.description(), command.description())) {
                            territory.setDescription(command.description());
                            changed = true;
                        }
                    }
                    if (!changed) {
                        return Uni.createFrom().item(Result.success(territory));
                    }
                    return Uni.createFrom().completionStage(territoryRepository.save(territory))
                            .flatMap(saved -> eventPublisher.publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved)));
                }));
    }
}