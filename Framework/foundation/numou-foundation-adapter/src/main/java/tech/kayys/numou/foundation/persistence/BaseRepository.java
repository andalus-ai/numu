package tech.kayys.numou.foundation.persistence;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;

import java.util.UUID;

/**
 * Base repository class providing Panache Reactive repository capabilities.
 *
 * @param <T> the entity type extending BaseEntity
 */
public abstract class BaseRepository<T extends BaseEntity> implements PanacheRepositoryBase<T, UUID> {
}
