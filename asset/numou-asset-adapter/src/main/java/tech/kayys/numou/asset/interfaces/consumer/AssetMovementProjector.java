package tech.kayys.numou.asset.interfaces.consumer;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.domain.event.*;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.movement.AssetMovement;
import tech.kayys.numou.asset.domain.movement.AssetMovementId;
import tech.kayys.numou.asset.domain.movement.AssetMovementType;
import tech.kayys.numou.asset.domain.repository.AssetMovementRepository;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.util.Objects;

/**
 * Turns ASSET-12 domain events into an append-only movement history (ASSET-13).
 *
 * <p>The consumer only reads events — it never mutates {@code Asset}, so the
 * history cannot corrupt the aggregate. Projection is idempotent: an event is
 * skipped when its {@code sourceEventId} is already recorded, which makes
 * at-least-once redelivery safe.</p>
 */
@ApplicationScoped
public class AssetMovementProjector {

    private final AssetMovementRepository movements;

    public AssetMovementProjector(AssetMovementRepository movements) {
        this.movements = Objects.requireNonNull(movements, "movements");
    }

    /**
     * Projects a single event. Events that do not describe a movement are ignored.
     */
    public Uni<Void> project(String tenantId, DomainEvent event) {
        AssetMovement movement = map(tenantId, event);
        if (movement == null) {
            return Uni.createFrom().nullItem();
        }
        return Uni.createFrom()
                .completionStage(() -> movements.existsBySourceEvent(event.eventId()))
                .flatMap(alreadyApplied -> Boolean.TRUE.equals(alreadyApplied)
                        ? Uni.createFrom().<Void>nullItem()
                        : Uni.createFrom().completionStage(() -> movements.append(movement))
                                .replaceWith((Void) null));
    }

    private static AssetMovement map(String tenantId, DomainEvent event) {
        if (event instanceof AssetLocationChanged e) {
            return build(tenantId, e.assetId(), AssetMovementType.LOCATION_CHANGED,
                    e.occurredAt(), null, null, e.locationId(), null, null, null, e.eventId());
        }
        if (event instanceof AssetLocationCleared e) {
            return build(tenantId, e.assetId(), AssetMovementType.LOCATION_CLEARED,
                    e.occurredAt(), null, null, null, null, null, null, e.eventId());
        }
        if (event instanceof AssetAssigned e) {
            return build(tenantId, e.assetId(), AssetMovementType.ASSIGNED,
                    e.occurredAt(), null, null, null, e.partyId(), null, null, e.eventId());
        }
        if (event instanceof AssetUnassigned e) {
            return build(tenantId, e.assetId(), AssetMovementType.UNASSIGNED,
                    e.occurredAt(), null, null, null, null, null, null, e.eventId());
        }
        if (event instanceof AssetClassified e) {
            return build(tenantId, e.assetId(), AssetMovementType.CLASSIFIED,
                    e.occurredAt(), null, null, null, null, e.classificationId(), null, e.eventId());
        }
        if (event instanceof AssetClassificationCleared e) {
            return build(tenantId, e.assetId(), AssetMovementType.CLASSIFICATION_CLEARED,
                    e.occurredAt(), null, null, null, null, null, null, e.eventId());
        }
        if (event instanceof AssetRelationshipCreated e) {
            return build(tenantId, e.sourceAssetId(), AssetMovementType.RELATIONSHIP_ADDED,
                    e.occurredAt(), null, null, null, null, null, e.relatedAssetId(), e.eventId());
        }
        if (event instanceof AssetRelationshipRemoved e) {
            return build(tenantId, e.sourceAssetId(), AssetMovementType.RELATIONSHIP_REMOVED,
                    e.occurredAt(), null, null, null, null, null, e.relatedAssetId(), e.eventId());
        }
        return null;
    }

    private static AssetMovement build(
            String tenantId,
            java.util.UUID assetId,
            AssetMovementType type,
            java.time.Instant occurredAt,
            String fromLocationId,
            String fromPartyId,
            String toLocationId,
            String toPartyId,
            String classificationId,
            java.util.UUID relatedAssetId,
            java.util.UUID sourceEventId
    ) {
        return new AssetMovement(
                AssetMovementId.generate(), tenantId, AssetId.of(assetId), type, occurredAt,
                fromLocationId, fromPartyId, toLocationId, toPartyId,
                classificationId, relatedAssetId, sourceEventId);
    }
}
