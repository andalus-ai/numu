package tech.kayys.numou.asset.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.application.hierarchy.AssetRelationshipHierarchy;
import tech.kayys.numou.asset.domain.event.AssetRelationshipCreated;
import tech.kayys.numou.asset.domain.relationship.AssetRelationship;
import tech.kayys.numou.asset.domain.relationship.AssetRelationshipPolicy;
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

public class AddAssetRelationshipHandler extends AbstractAssetCommandHandler
        implements CommandHandler<AddAssetRelationshipCommand, Result<UUID>> {

    private final AssetRelationshipRepository relationships;

    private final AssetRelationshipHierarchy hierarchy;

    public AddAssetRelationshipHandler(AssetRepository repository, AssetRelationshipRepository relationships,
                                       EventPublisher eventPublisher, UnitOfWork unitOfWork, DomainClock clock) {
        this(repository, relationships, eventPublisher, unitOfWork, clock, null);
    }

    public AddAssetRelationshipHandler(AssetRepository repository, AssetRelationshipRepository relationships,
                                       EventPublisher eventPublisher, UnitOfWork unitOfWork, DomainClock clock,
                                       AssetRelationshipHierarchy hierarchy) {
        super(repository, eventPublisher, unitOfWork, clock);
        this.relationships = relationships;
        this.hierarchy = hierarchy;
    }

    @Override
    public Uni<Result<UUID>> handle(AddAssetRelationshipCommand command) {
        try {
            AssetRelationshipPolicy.validateNewRelationship(
                    command.sourceAssetId().value(), command.relatedAssetId().value(),
                    command.relationshipType());
        } catch (IllegalArgumentException | NullPointerException bad) {
            return Uni.createFrom().failure(new ApplicationErrorException(
                    ApplicationError.of("asset.invalid-argument", bad.getMessage())));
        }
        return requireAsset(command.tenantId(), command.sourceAssetId())
                .flatMap(source -> Uni.createFrom()
                        .completionStage(() -> repository.existsByTenantAndId(command.tenantId(), command.relatedAssetId()))
                        .flatMap(exists -> {
                            if (!Boolean.TRUE.equals(exists)) {
                                return Uni.createFrom().failure(new ApplicationErrorException(
                                        ApplicationError.of("asset.not-found",
                                                "Related asset not found: " + command.relatedAssetId().value())));
                            }
                            if (source.status().isTerminal()) {
                                return Uni.createFrom().failure(new ApplicationErrorException(
                                        ApplicationError.of("asset.invalid-state",
                                                "Disposed asset cannot form new relationships")));
                            }
                            return checkIntegrity(command);
                        }));
    }

    private Uni<Result<UUID>> checkIntegrity(AddAssetRelationshipCommand command) {
        return Uni.createFrom()
                .completionStage(() -> relationships.findBySource(
                        command.tenantId(), command.sourceAssetId()))
                .flatMap(existing -> {
                    boolean duplicate = existing.stream().anyMatch(r ->
                            r.relatedAssetId().equals(command.relatedAssetId())
                                    && r.type() == command.relationshipType());
                    if (duplicate) {
                        return Uni.createFrom().failure(new ApplicationErrorException(
                                ApplicationError.of("asset.relationship.duplicate",
                                        "Relationship already exists")));
                    }
                    if (command.relationshipType().hierarchical()
                            && existing.stream().anyMatch(r -> r.type().hierarchical())) {
                        return Uni.createFrom().failure(new ApplicationErrorException(
                                ApplicationError.of("asset.relationship.parent-exists",
                                        "Source already has a hierarchical parent")));
                    }
                    if (command.relationshipType().hierarchical() && hierarchy != null) {
                        return Uni.createFrom()
                                .completionStage(() -> hierarchy.wouldCreateCycle(
                                        command.tenantId(), command.sourceAssetId().value(),
                                        command.relatedAssetId().value()))
                                .flatMap(cycle -> cycle ? cycleFailure() : persist(command));
                    }
                    return persist(command);
                });
    }

    private static Uni<Result<UUID>> cycleFailure() {
        return Uni.createFrom().failure(new ApplicationErrorException(
                ApplicationError.of("asset.relationship.cycle",
                        "Relationship would create a cycle")));
    }

    private Uni<Result<UUID>> persist(AddAssetRelationshipCommand command) {
        AssetRelationship relationship = AssetRelationship.of(
                command.tenantId(), command.sourceAssetId(), command.relatedAssetId(),
                command.relationshipType(), clock().now());
        return unitOfWork.execute(() -> Uni.createFrom()
                        .completionStage(() -> relationships.save(relationship))
                        .flatMap(saved -> eventPublisher.publish(List.of(new AssetRelationshipCreated(
                                UUID.randomUUID(), clock().now(),
                                saved.sourceAssetId().value(), saved.relatedAssetId().value(),
                                saved.type().name()))).replaceWith(saved)))
                .map(saved -> Result.success(saved.id().value()))
                .onFailure(IllegalStateException.class).transform(this::mapConstraint);
    }

    private RuntimeException mapConstraint(Throwable failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage().toLowerCase();
        if (message.contains("ux_asset_hierarchical_parent")) {
            return new ApplicationErrorException(ApplicationError.of(
                    "asset.relationship.parent-exists",
                    "Source already has a hierarchical parent"));
        }
        if (message.contains("uq_asset_relationship")) {
            return new ApplicationErrorException(ApplicationError.of(
                    "asset.relationship.duplicate", "Relationship already exists"));
        }
        return failure instanceof RuntimeException runtime ? runtime : new RuntimeException(failure);
    }
}
