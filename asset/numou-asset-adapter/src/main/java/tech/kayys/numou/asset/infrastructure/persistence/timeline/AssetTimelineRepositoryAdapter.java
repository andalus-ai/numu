package tech.kayys.numou.asset.infrastructure.persistence.timeline;

import io.quarkus.hibernate.reactive.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.application.timeline.AssetTimelineEntry;
import tech.kayys.numou.asset.application.timeline.AssetTimelineRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Panache-reactive adapter for the timeline read model (ASSET-27 §30).
 *
 * <p>Idempotency is enforced two ways: a cheap pre-check and a unique index on
 * {@code (tenant_id, source_event_id)}; concurrent duplicate projections simply
 * fail the insert and are treated as "already applied".</p>
 */
@ApplicationScoped
public class AssetTimelineRepositoryAdapter implements AssetTimelineRepository {

    @Override
    public CompletionStage<Boolean> appendIfAbsent(AssetTimelineEntry entry) {
        return Panache.withTransaction(() -> AssetTimelineEntity
                        .<AssetTimelineEntity>count("sourceEventId = ?1", entry.sourceEventId())
                        .flatMap(count -> count > 0
                                ? io.smallrye.mutiny.Uni.createFrom().item(Boolean.FALSE)
                                : AssetTimelineEntity.persist(toEntity(entry)).replaceWith(Boolean.TRUE)))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsBySourceEvent(UUID sourceEventId) {
        return Panache.withSession(() -> AssetTimelineEntity
                        .<AssetTimelineEntity>count("sourceEventId = ?1", sourceEventId))
                .map(count -> count > 0)
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<List<AssetTimelineEntry>> findByAsset(String tenantId, UUID assetId) {
        return Panache.withSession(() -> AssetTimelineEntity
                        .<AssetTimelineEntity>list(
                                "tenantId = ?1 and assetId = ?2 order by occurredAt asc, sourceEventId asc",
                                tenantId, assetId)
                        .map(entities -> entities.stream()
                                .map(AssetTimelineRepositoryAdapter::toDomain)
                                .collect(Collectors.toList())))
                .subscribe().asCompletionStage();
    }

    static AssetTimelineEntity toEntity(AssetTimelineEntry entry) {
        AssetTimelineEntity entity = new AssetTimelineEntity();
        entity.id = entry.id();
        entity.tenantId = entry.tenantId();
        entity.assetId = entry.assetId();
        entity.occurredAt = entry.occurredAt();
        entity.eventType = entry.eventType();
        entity.category = entry.category();
        entity.title = entry.title();
        entity.description = entry.description();
        entity.source = entry.source();
        entity.sourceReference = entry.sourceReference();
        entity.sourceEventId = entry.sourceEventId();
        entity.createdAt = Instant.now();
        return entity;
    }

    static AssetTimelineEntry toDomain(AssetTimelineEntity entity) {
        return new AssetTimelineEntry(
                entity.id,
                entity.tenantId,
                entity.assetId,
                entity.occurredAt,
                entity.eventType,
                entity.category,
                entity.title,
                entity.description,
                entity.source,
                entity.sourceReference,
                entity.sourceEventId);
    }
}