package tech.kayys.numou.asset.application.installation;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.event.AssetComponentInstalled;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.installation.AssetInstallation;
import tech.kayys.numou.asset.domain.relationship.AssetRelationship;
import tech.kayys.numou.asset.domain.relationship.AssetRelationshipType;
import tech.kayys.numou.asset.domain.repository.AssetInstallationRepository;
import tech.kayys.numou.asset.domain.repository.AssetRelationshipRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class InstallAssetComponentHandler implements CommandHandler<InstallAssetComponentCommand, Result<UUID>> {

    private final AssetRepository assets;
    private final AssetRelationshipRepository relationships;
    private final AssetInstallationRepository installations;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;
    private final DomainClock clock;

    public InstallAssetComponentHandler(AssetRepository assets, AssetRelationshipRepository relationships,
                                       AssetInstallationRepository installations, EventPublisher eventPublisher,
                                       UnitOfWork unitOfWork, DomainClock clock) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.relationships = Objects.requireNonNull(relationships, "relationships");
        this.installations = Objects.requireNonNull(installations, "installations");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Uni<Result<UUID>> handle(InstallAssetComponentCommand command) {
        if (command.componentAssetId().equals(command.parentAssetId())) {
            return Uni.createFrom().failure(new ApplicationErrorException(
                    ApplicationError.of("asset.installation.invalid", "An asset cannot be installed on itself")));
        }
        if (command.relationshipType() == AssetRelationshipType.LINKED) {
            return Uni.createFrom().failure(new ApplicationErrorException(
                    ApplicationError.of("asset.installation.invalid", "LINKED is not a valid installation relationship")));
        }
        AssetId componentId = AssetId.of(command.componentAssetId());
        AssetId parentId = AssetId.of(command.parentAssetId());
        return Uni.createFrom()
                .completionStage(() -> assets.findByTenantAndId(command.tenantId(), componentId))
                .flatMap(component -> {
                    if (component.isEmpty()) {
                        return Uni.createFrom().failure(new ApplicationErrorException(
                                ApplicationError.of("asset.not-found", "Component asset not found: " + command.componentAssetId())));
                    }
                    return Uni.createFrom()
                            .completionStage(() -> assets.findByTenantAndId(command.tenantId(), parentId))
                            .flatMap(parent -> {
                                if (parent.isEmpty()) {
                                    return Uni.createFrom().failure(new ApplicationErrorException(
                                            ApplicationError.of("asset.not-found", "Parent asset not found: " + command.parentAssetId())));
                                }
                                return Uni.createFrom().completionStage(() -> installations.existsActive(command.tenantId(), command.componentAssetId()))
                                        .flatMap(hasActive -> {
                                            if (Boolean.TRUE.equals(hasActive)) {
                                                return Uni.createFrom().failure(new ApplicationErrorException(
                                                        ApplicationError.of("asset.installation.conflict", "Component already has an active parent")));
                                            }
                                            return Uni.createFrom().completionStage(() -> relationships.findBySource(command.tenantId(), componentId))
                                                    .flatMap(existing -> {
                                                        boolean hasParent = existing.stream().anyMatch(r -> isHierarchical(r.type()));
                                                        if (hasParent) {
                                                            return Uni.createFrom().failure(new ApplicationErrorException(
                                                                    ApplicationError.of("asset.installation.conflict", "Component already has an active parent")));
                                                        }
                                                        return checkNoCycle(command.tenantId(), command.componentAssetId(), command.parentAssetId());
                                                    });
                                        });
                            });
                })
                .flatMap(ignored -> {
                    Instant now = clock.now();
                    AssetRelationship relationship = AssetRelationship.of(command.tenantId(), componentId, parentId, command.relationshipType(), now);
                    AssetInstallation installation = AssetInstallation.install(command.tenantId(), command.componentAssetId(), command.parentAssetId(), command.relationshipType(), now, command.installedBy());
                    return unitOfWork.execute(() -> Uni.createFrom()
                            .completionStage(() -> relationships.save(relationship))
                            .flatMap(saved -> Uni.createFrom()
                                    .completionStage(() -> installations.save(installation))
                                    .flatMap(stored -> eventPublisher.publish(List.of(new AssetComponentInstalled(UUID.randomUUID(), now, command.componentAssetId(), command.parentAssetId(), command.relationshipType(), stored.id().value(), command.installedBy()))).replaceWith(stored))))
                            .map(stored -> Result.success(stored.id().value()));
                });
    }

    private Uni<Void> checkNoCycle(String tenantId, UUID componentId, UUID parentId) {
        Deque<UUID> queue = new ArrayDeque<>(List.of(parentId));
        return walkAncestors(tenantId, componentId, queue, new HashSet<>());
    }

    private Uni<Void> walkAncestors(String tenantId, UUID componentId, Deque<UUID> queue, Set<UUID> visited) {
        UUID current = queue.poll();
        if (current == null) {
            return Uni.createFrom().voidItem();
        }
        if (!visited.add(current)) {
            return walkAncestors(tenantId, componentId, queue, visited);
        }
        return Uni.createFrom()
                .completionStage(() -> relationships.findBySource(tenantId, AssetId.of(current)))
                .flatMap(rels -> {
                    for (AssetRelationship rel : rels) {
                        if (!isHierarchical(rel.type())) {
                            continue;
                        }
                        UUID next = rel.relatedAssetId().value();
                        if (next.equals(componentId)) {
                            return Uni.createFrom().failure(new ApplicationErrorException(
                                    ApplicationError.of("asset.installation.cycle", "Installation would create a cycle")));
                        }
                        if (!visited.contains(next)) {
                            queue.add(next);
                        }
                    }
                    return walkAncestors(tenantId, componentId, queue, visited);
                });
    }

    static boolean isHierarchical(AssetRelationshipType type) {
        return type != AssetRelationshipType.LINKED;
    }
}
