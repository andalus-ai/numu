package tech.kayys.numou.crm.infrastructure.persistence.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import tech.kayys.numou.crm.domain.identifier.CustomerId;
import tech.kayys.numou.crm.domain.identifier.OpportunityId;
import tech.kayys.numou.crm.domain.model.Opportunity;
import tech.kayys.numou.crm.domain.repository.OpportunityRepository;
import tech.kayys.numou.crm.domain.valueobject.OpportunityStage;
import tech.kayys.numou.crm.infrastructure.persistence.entity.OpportunityEntity;
import tech.kayys.numou.crm.infrastructure.persistence.mapper.OpportunityMapper;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Implementation of {@link OpportunityRepository} using Hibernate Reactive Panache.
 */
@ApplicationScoped
public class OpportunityRepositoryImpl implements OpportunityRepository {

    private final OpportunityMapper mapper;

    public OpportunityRepositoryImpl(OpportunityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CompletionStage<Opportunity> save(Opportunity opportunity) {
        OpportunityEntity entity = mapper.toEntity(opportunity);

        if (entity.id == null) {
            entity.id = UUID.randomUUID();
        }

        return Panache.withTransaction(() -> entity.<OpportunityEntity>persist()
            .map(v -> {
                opportunity.clearEvents();
                return opportunity;
            })
        ).subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<Opportunity>> findById(OpportunityId id) {
    return Panache.withSession(() -> OpportunityEntity.<OpportunityEntity>findById(id.getValue())
            .map(entity -> entity == null
                    ? Optional.<Opportunity>empty()
                    : Optional.of(mapper.toDomain((OpportunityEntity) entity)))
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsById(OpportunityId id) {
    return Panache.withSession(() -> OpportunityEntity.<OpportunityEntity>findById(id.getValue())
            .map(entity -> entity != null)
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<Void> delete(Opportunity opportunity) {
    return Panache.withTransaction(() -> OpportunityEntity.deleteById(opportunity.getId().getValue())
            .map(v -> (Void) null)
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<Void> deleteById(OpportunityId id) {
    return Panache.withTransaction(() -> OpportunityEntity.deleteById(id.getValue())
            .map(v -> (Void) null)
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<List<Opportunity>> findByCustomerId(CustomerId customerId) {
    return Panache.withSession(() -> OpportunityEntity.<OpportunityEntity>list("customerId = ?1", customerId.getValue())
            .map(this::toDomainList)
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<List<Opportunity>> findByStage(OpportunityStage stage) {
    return Panache.withSession(() -> OpportunityEntity.<OpportunityEntity>list("stage = ?1", stage)
            .map(this::toDomainList)
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<List<Opportunity>> findActiveOpportunities() {
    return Panache.withSession(() -> OpportunityEntity.<OpportunityEntity>list("active = true")
            .map(this::toDomainList)
            ).subscribe()
            .asCompletionStage();
    }

    private List<Opportunity> toDomainList(List<OpportunityEntity> entities) {
        return entities.stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }
}
