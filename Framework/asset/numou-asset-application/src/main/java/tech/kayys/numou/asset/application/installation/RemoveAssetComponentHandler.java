package tech.kayys.numou.asset.application.installation;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.event.AssetComponentRemoved;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.installation.AssetInstallation;
import tech.kayys.numou.asset.domain.relationship.AssetRelationship;
import tech.kayys.numou.asset.domain.repository.AssetInstallationRepository;
import tech.kayys.numou.asset.domain.repository.AssetRelationshipRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class RemoveAssetComponentHandler implements CommandHandler<RemoveAssetComponentCommand, Result<UUID>> {

    private final AssetRelationshipRepository relationships;
    private final AssetInstallationRepository installations;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;
    private final DomainClock clock;

    public RemoveAssetComponentHandler(AssetRelationshipRepository relationships,
                                       AssetInstallationRepository installations, EventPublisher eventPublisher,
                                       UnitOfWork unitOfWork, DomainClock clock) {
        this.relationships = Objects.requireNonNull(relationships, "relationships");
        this.installations = Objects.requireNonNull(installations, "installations");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Uni<Result<UUID>> handle(RemoveAssetComponentCommand command) {
        return Uni.createFrom()
                .completionStage(() -> installations.findActive(command.tenantId(), command.componentAssetId(), command.parentAssetId()))
                .flatMap(active -> {
                    if (active.isEmpty()) {
                        return Uni.createFrom().failure(new ApplicationErrorException(
                                ApplicationError.of("asset.installation.not-found", "Active installation not found")));
                    }
                    AssetInstallation installation = active.get();
                    return Uni.createFrom()
                            .completionStage(() -> relationships.findBySource(command.tenantId(), AssetId.of(command.componentAssetId())))
                            .flatMap(rels -> {
                                AssetRelationship match = null;
                                for (AssetRelationship rel : rels) {
                                    if (rel.relatedAssetId().value().equals(command.parentAssetId())) {
                                        match = rel;
                                        break;
                                    }
                                }
                                if (match == null) {
                                    return Uni.createFrom().failure(new ApplicationErrorException(
                                            ApplicationError.of("asset.installation.not-found", "Relationship for installation not found")));
                                }
                                AssetRelationship target = match;
                                Instant now = clock.now();
                                AssetInstallation removed;
                                try {
                                    removed = installation.markRemoved(now, command.removedBy());
                                } catch (IllegalStateException | NullPointerException e) {
                                    return Uni.createFrom().failure(new ApplicationErrorException(
                                            ApplicationError.of("asset.installation.conflict", "Installation already removed")));
                                }
                                return unitOfWork.execute(() -> Uni.createFrom()
                                        .completionStage(() -> relationships.delete(target))
                                        .flatMap(ignored -> Uni.createFrom().completionStage(() -> installations.save(removed)))
                                        .flatMap(stored -> eventPublisher.publish(List.of(new AssetComponentRemoved(UUID.randomUUID(), now, command.componentAssetId(), command.parentAssetId(), stored.id().value(), command.removedBy()))).replaceWith(stored)))
                                        .map(stored -> Result.success(stored.id().value()));
                            });
                });
    }
}
