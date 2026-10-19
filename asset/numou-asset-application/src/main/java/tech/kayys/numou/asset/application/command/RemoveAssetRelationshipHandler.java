package tech.kayys.numou.asset.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.event.AssetRelationshipRemoved;
import tech.kayys.numou.asset.domain.relationship.AssetRelationship;
import tech.kayys.numou.asset.domain.repository.AssetRelationshipRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.List;
import java.util.UUID;

public class RemoveAssetRelationshipHandler extends AbstractAssetCommandHandler
        implements CommandHandler<RemoveAssetRelationshipCommand, Result<UUID>> {

    private final AssetRelationshipRepository relationships;

    public RemoveAssetRelationshipHandler(AssetRepository repository, AssetRelationshipRepository relationships,
                                          EventPublisher eventPublisher, UnitOfWork unitOfWork, DomainClock clock) {
        super(repository, eventPublisher, unitOfWork, clock);
        this.relationships = relationships;
    }

    @Override
    public Uni<Result<UUID>> handle(RemoveAssetRelationshipCommand command) {
        return Uni.createFrom()
                .completionStage(() -> relationships.findById(command.relationshipId()))
                .map(opt -> opt.orElse(null))
                .onItem().ifNull().failWith(() -> new ApplicationErrorException(
                        ApplicationError.of("asset.relationship.not-found",
                                "Relationship not found: " + command.relationshipId().value())))
                .flatMap(relationship -> {
                    if (!relationship.tenantId().equals(command.tenantId())) {
                        return Uni.createFrom().failure(new ApplicationErrorException(
                                ApplicationError.of("asset.relationship.forbidden",
                                        "Relationship belongs to another tenant")));
                    }
                    // ASSET-17 §17.20/17.21: removal is always allowed — detached
                    // subassemblies are valid, cascades are forbidden, and
                    // DISPOSED assets may have relationships removed.
                    return unitOfWork.execute(() -> Uni.createFrom()
                            .completionStage(() -> relationships.delete(relationship))
                            .flatMap(ignored -> eventPublisher.publish(List.of(new AssetRelationshipRemoved(
                                    UUID.randomUUID(), clock().now(),
                                    relationship.sourceAssetId().value(),
                                    relationship.relatedAssetId().value(),
                                    relationship.type().name())))));
                })
                .map(ignored -> Result.success(command.relationshipId().value()));
    }
}
