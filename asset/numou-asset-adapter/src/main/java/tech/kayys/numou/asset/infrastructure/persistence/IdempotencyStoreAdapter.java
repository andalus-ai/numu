package tech.kayys.numou.asset.infrastructure.persistence;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.application.integration.IdempotencyRecord;
import tech.kayys.numou.asset.application.integration.IdempotencyStore;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Panache-reactive idempotency store (ASSET-28 §47).
 *
 * <p>Uniqueness is enforced by {@code (tenant_id, idempotency_key)} at the
 * database so two concurrent retries cannot both be treated as "new": the second
 * insert is rejected and reported as an existing key.</p>
 */
@ApplicationScoped
public class IdempotencyStoreAdapter implements IdempotencyStore {

    @Override
    public CompletionStage<Optional<IdempotencyRecord>> find(String tenantId, String key) {
        return Panache.withSession(() -> IdempotencyEntity
                        .<IdempotencyEntity>find(
                                "tenantId = ?1 and idempotencyKey = ?2", tenantId, key)
                        .firstResult())
                .map(e -> e == null ? Optional.<IdempotencyRecord>empty() : Optional.of(toDomain(e)))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> record(IdempotencyRecord record) {
        return Panache.withTransaction(() -> IdempotencyEntity
                        .<IdempotencyEntity>count(
                                "tenantId = ?1 and idempotencyKey = ?2", record.tenantId(), record.key())
                        .flatMap(count -> count > 0
                                ? Uni.createFrom().item(Boolean.FALSE)
                                : IdempotencyEntity.persist(toEntity(record)).replaceWith(Boolean.TRUE)))
                .onFailure().recoverWithItem(Boolean.FALSE)
                .subscribe().asCompletionStage();
    }

    static IdempotencyEntity toEntity(IdempotencyRecord record) {
        IdempotencyEntity entity = new IdempotencyEntity();
        entity.tenantId = record.tenantId();
        entity.idempotencyKey = record.key();
        entity.operation = record.operation();
        entity.requestFingerprint = record.requestFingerprint();
        entity.storedAt = record.storedAt();
        return entity;
    }

    static IdempotencyRecord toDomain(IdempotencyEntity entity) {
        return new IdempotencyRecord(
                entity.tenantId,
                entity.idempotencyKey,
                entity.operation,
                entity.requestFingerprint,
                entity.storedAt);
    }
}