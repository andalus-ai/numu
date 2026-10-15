package tech.kayys.syirkah.crm.infrastructure.persistence.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.crm.domain.repository.TerritoryRepository;
import tech.kayys.syirkah.crm.domain.territory.Territory;
import tech.kayys.syirkah.crm.domain.territory.TerritoryId;
import tech.kayys.syirkah.crm.infrastructure.persistence.entity.TerritoryEntity;
import tech.kayys.syirkah.crm.infrastructure.persistence.mapper.TerritoryMapper;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Implementation of {@link TerritoryRepository} using Hibernate Reactive Panache.
 */
@ApplicationScoped
public class TerritoryRepositoryImpl implements TerritoryRepository {

    private final TerritoryMapper mapper;

    public TerritoryRepositoryImpl(TerritoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CompletionStage<Territory> save(Territory territory) {
        TerritoryEntity entity = mapper.toEntity(territory);
        return Panache.withTransaction(() -> Panache.getSession()
                .flatMap(session -> session.merge(entity))
                .replaceWith(territory)
                .map(v -> {
                    territory.clearEvents();
                    return territory;
                }))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<Territory>> findById(TerritoryId id) {
    return Panache.withSession(() -> TerritoryEntity.<TerritoryEntity>findById(id.getValue())
                .map(entity -> entity == null ? Optional.<Territory>empty() : Optional.of(mapper.toDomain(entity)))
                ).subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsById(TerritoryId id) {
    return Panache.withSession(() -> TerritoryEntity.<TerritoryEntity>findById(id.getValue()).map(entity -> entity != null)).subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Void> delete(Territory territory) {
        return deleteById(territory.id());
    }

    @Override
    public CompletionStage<Void> deleteById(TerritoryId id) {
    return Panache.withTransaction(() -> TerritoryEntity.deleteById(id.getValue())
                .map(v -> (Void) null)
                ).subscribe().asCompletionStage();
    }
}
