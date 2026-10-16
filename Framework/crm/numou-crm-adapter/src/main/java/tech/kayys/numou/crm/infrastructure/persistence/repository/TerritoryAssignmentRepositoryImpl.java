package tech.kayys.numou.crm.infrastructure.persistence.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.domain.repository.TerritoryAssignmentRepository;
import tech.kayys.numou.crm.domain.territory.TerritoryAssignment;
import tech.kayys.numou.crm.domain.territory.TerritoryAssignmentId;
import tech.kayys.numou.crm.infrastructure.persistence.entity.TerritoryAssignmentEntity;
import tech.kayys.numou.crm.infrastructure.persistence.mapper.TerritoryAssignmentMapper;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Implementation of {@link TerritoryAssignmentRepository} using Hibernate Reactive Panache.
 */
@ApplicationScoped
public class TerritoryAssignmentRepositoryImpl implements TerritoryAssignmentRepository {

    private final TerritoryAssignmentMapper mapper;

    public TerritoryAssignmentRepositoryImpl(TerritoryAssignmentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CompletionStage<TerritoryAssignment> save(TerritoryAssignment assignment) {
        TerritoryAssignmentEntity entity = mapper.toEntity(assignment);
        return Panache.withTransaction(() -> Panache.getSession()
                .flatMap(session -> session.merge(entity))
                .replaceWith(assignment)
                .map(v -> {
                    assignment.clearEvents();
                    return assignment;
                }))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<TerritoryAssignment>> findById(TerritoryAssignmentId id) {
    return Panache.withSession(() -> TerritoryAssignmentEntity.<TerritoryAssignmentEntity>findById(id.getValue())
                .map(entity -> entity == null
                        ? Optional.<TerritoryAssignment>empty()
                        : Optional.of(mapper.toDomain(entity)))
                ).subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsById(TerritoryAssignmentId id) {
    return Panache.withSession(() -> TerritoryAssignmentEntity.<TerritoryAssignmentEntity>findById(id.getValue()).map(entity -> entity != null)).subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Void> delete(TerritoryAssignment assignment) {
        return deleteById(assignment.id());
    }

    @Override
    public CompletionStage<Void> deleteById(TerritoryAssignmentId id) {
    return Panache.withTransaction(() -> TerritoryAssignmentEntity.deleteById(id.getValue())
                .map(v -> (Void) null)
                ).subscribe().asCompletionStage();
    }
}
