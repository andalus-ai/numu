package tech.kayys.numou.asset.infrastructure.persistence;

import io.quarkus.hibernate.reactive.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.domain.availability.AssetAvailabilityPeriod;
import tech.kayys.numou.asset.domain.availability.AssetAvailabilityPeriodId;
import tech.kayys.numou.asset.domain.availability.AssetAvailabilityType;
import tech.kayys.numou.asset.domain.repository.AssetAvailabilityRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/** Panache-reactive adapter for {@link AssetAvailabilityRepository} (ASSET-26 §8). */
@ApplicationScoped
public class AssetAvailabilityRepositoryAdapter implements AssetAvailabilityRepository {

    @Override
    public CompletionStage<AssetAvailabilityPeriod> save(String tenantId, AssetAvailabilityPeriod period) {
        AssetAvailabilityEntity entity = toEntity(period);
        return Panache.withTransaction(() -> Panache.getSession()
                        .flatMap(session -> session.<AssetAvailabilityEntity>merge(entity))
                        .replaceWith(period))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<AssetAvailabilityPeriod>> findById(
            String tenantId, AssetAvailabilityPeriodId id) {
        return Panache.withSession(() -> AssetAvailabilityEntity
                        .<AssetAvailabilityEntity>find("tenantId = ?1 and id = ?2", tenantId, id.value())
                        .firstResult())
                .map(e -> e == null ? Optional.<AssetAvailabilityPeriod>empty() : Optional.of(toDomain(e)))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<List<AssetAvailabilityPeriod>> findByAssetId(
            String tenantId, UUID assetId, Instant from, Instant to) {
        return Panache.withSession(() -> AssetAvailabilityEntity
                        .<AssetAvailabilityEntity>list(
                                "tenantId = ?1 and assetId = ?2 order by startsAt asc", tenantId, assetId)
                        .map(entities -> entities.stream()
                                .map(AssetAvailabilityRepositoryAdapter::toDomain)
                                .filter(period -> inWindow(period, from, to))
                                .collect(Collectors.toList())))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> hasOverlap(
            String tenantId, UUID assetId, AssetAvailabilityType type, Instant startsAt, Instant endsAt) {
        return Panache.withSession(() -> AssetAvailabilityEntity
                        .<AssetAvailabilityEntity>list(
                                "tenantId = ?1 and assetId = ?2 and availabilityType = ?3",
                                tenantId, assetId, type)
                        .map(entities -> entities.stream()
                                .map(AssetAvailabilityRepositoryAdapter::toDomain)
                                .anyMatch(period -> period.overlaps(startsAt, endsAt))))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<AssetAvailabilityPeriod>> findOpen(String tenantId, UUID assetId) {
        return Panache.withSession(() -> AssetAvailabilityEntity
                        .<AssetAvailabilityEntity>find(
                                "tenantId = ?1 and assetId = ?2 and endsAt is null order by startsAt asc",
                                tenantId, assetId)
                        .firstResult())
                .map(e -> e == null ? Optional.<AssetAvailabilityPeriod>empty() : Optional.of(toDomain(e)))
                .subscribe().asCompletionStage();
    }

    private static boolean inWindow(AssetAvailabilityPeriod period, Instant from, Instant to) {
        if (from == null && to == null) {
            return true;
        }
        Instant windowStart = from == null ? Instant.MIN : from;
        Instant windowEnd = to == null ? null : to;
        return period.overlaps(windowStart, windowEnd);
    }

    static AssetAvailabilityEntity toEntity(AssetAvailabilityPeriod period) {
        AssetAvailabilityEntity entity = new AssetAvailabilityEntity();
        entity.id = period.id().value();
        entity.tenantId = period.tenantId();
        entity.assetId = period.assetId();
        entity.startsAt = period.startsAt();
        entity.endsAt = period.endsAt();
        entity.availabilityType = period.type();
        entity.reason = period.reason();
        entity.referenceId = period.referenceId();
        entity.notes = period.notes();
        entity.createdAt = Instant.now();
        return entity;
    }

    static AssetAvailabilityPeriod toDomain(AssetAvailabilityEntity entity) {
        return AssetAvailabilityPeriod.of(
                AssetAvailabilityPeriodId.of(entity.id),
                entity.tenantId,
                entity.assetId,
                entity.startsAt,
                entity.endsAt,
                entity.availabilityType,
                entity.reason,
                entity.referenceId,
                entity.notes);
    }
}