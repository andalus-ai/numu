package tech.kayys.numou.asset.infrastructure.persistence;

import io.quarkus.hibernate.reactive.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.domain.availability.AssetUtilizationRecord;
import tech.kayys.numou.asset.domain.availability.AssetUtilizationRecordId;
import tech.kayys.numou.asset.domain.repository.AssetUtilizationRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/** Panache-reactive adapter for {@link AssetUtilizationRepository} (ASSET-26 §15). */
@ApplicationScoped
public class AssetUtilizationRepositoryAdapter implements AssetUtilizationRepository {

    @Override
    public CompletionStage<AssetUtilizationRecord> save(String tenantId, AssetUtilizationRecord record) {
        AssetUtilizationEntity entity = toEntity(record);
        return Panache.withTransaction(() -> Panache.getSession()
                        .flatMap(session -> session.<AssetUtilizationEntity>merge(entity))
                        .replaceWith(record))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<AssetUtilizationRecord>> findBySourceRef(
            String tenantId, String source, String referenceId) {
        return Panache.withSession(() -> AssetUtilizationEntity
                        .<AssetUtilizationEntity>find(
                                "tenantId = ?1 and source = ?2 and referenceId = ?3",
                                tenantId, source, referenceId)
                        .firstResult())
                .map(e -> e == null ? Optional.<AssetUtilizationRecord>empty() : Optional.of(toDomain(e)))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<List<AssetUtilizationRecord>> findByAssetId(
            String tenantId, UUID assetId, Instant from, Instant to) {
        return Panache.withSession(() -> AssetUtilizationEntity
                        .<AssetUtilizationEntity>list(
                                "tenantId = ?1 and assetId = ?2 order by startsAt asc", tenantId, assetId)
                        .map(entities -> entities.stream()
                                .map(AssetUtilizationRepositoryAdapter::toDomain)
                                .filter(record -> inWindow(record, from, to))
                                .collect(Collectors.toList())))
                .subscribe().asCompletionStage();
    }

    private static boolean inWindow(AssetUtilizationRecord record, Instant from, Instant to) {
        if (from != null && record.endsAt().isBefore(from)) {
            return false;
        }
        return to == null || !record.startsAt().isAfter(to);
    }

    static AssetUtilizationEntity toEntity(AssetUtilizationRecord record) {
        AssetUtilizationEntity entity = new AssetUtilizationEntity();
        entity.id = record.id().value();
        entity.tenantId = record.tenantId();
        entity.assetId = record.assetId();
        entity.startsAt = record.startsAt();
        entity.endsAt = record.endsAt();
        entity.utilizationType = record.type();
        entity.quantity = record.quantity();
        entity.unit = record.unit();
        entity.source = record.source();
        entity.referenceId = record.referenceId();
        entity.occurredAt = record.occurredAt();
        entity.createdAt = Instant.now();
        return entity;
    }

    static AssetUtilizationRecord toDomain(AssetUtilizationEntity entity) {
        return new AssetUtilizationRecord(
                AssetUtilizationRecordId.of(entity.id),
                entity.tenantId,
                entity.assetId,
                entity.startsAt,
                entity.endsAt,
                entity.utilizationType,
                entity.quantity,
                entity.unit,
                entity.source,
                entity.referenceId,
                entity.occurredAt);
    }
}