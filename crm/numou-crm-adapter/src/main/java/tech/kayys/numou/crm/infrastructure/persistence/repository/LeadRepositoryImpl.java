package tech.kayys.numou.crm.infrastructure.persistence.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import tech.kayys.numou.crm.domain.identifier.LeadId;
import tech.kayys.numou.crm.domain.model.Lead;
import tech.kayys.numou.crm.domain.repository.LeadRepository;
import tech.kayys.numou.crm.domain.valueobject.LeadStatus;
import tech.kayys.numou.crm.infrastructure.persistence.entity.LeadEntity;
import tech.kayys.numou.crm.infrastructure.persistence.mapper.LeadMapper;
import tech.kayys.numou.identity.domain.user.UserId;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Implementation of LeadRepository using Hibernate Reactive Panache.
 */
@ApplicationScoped
public class LeadRepositoryImpl implements LeadRepository {

    private final LeadMapper mapper;

    public LeadRepositoryImpl(LeadMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CompletionStage<Lead> save(Lead lead) {
        LeadEntity entity = mapper.toEntity(lead);
        
        if (entity.id == null) {
            entity.id = UUID.randomUUID();
        }
        
        return Panache.withTransaction(() -> entity.<LeadEntity>persist()
            .map(v -> {
                lead.clearEvents();
                return lead;
            })
        ).subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<Lead>> findById(LeadId id) {
    return Panache.withSession(() -> LeadEntity.<LeadEntity>findById(id.getValue())
            .map(entity -> entity == null ? Optional.<Lead>empty() : Optional.of(mapper.toDomain(entity)))
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsById(LeadId id) {
    return Panache.withSession(() -> LeadEntity.<LeadEntity>findById(id.getValue())
            .map(entity -> entity != null)
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<Void> delete(Lead lead) {
    return Panache.withTransaction(() -> LeadEntity.deleteById(lead.getId().getValue())
            .map(v -> (Void) null)
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<Void> deleteById(LeadId id) {
    return Panache.withTransaction(() -> LeadEntity.deleteById(id.getValue())
            .map(v -> (Void) null)
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<List<Lead>> findByStatus(LeadStatus status) {
    return Panache.withSession(() -> LeadEntity.<LeadEntity>list("status = ?1", status)
            .map(entities -> entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList()))
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<List<Lead>> findByEmail(String email) {
    return Panache.withSession(() -> LeadEntity.<LeadEntity>list("email = ?1", email)
            .map(entities -> entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList()))
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<List<Lead>> findByAssignedTo(UserId assignedTo) {
    return Panache.withSession(() -> LeadEntity.<LeadEntity>list("assignedTo = ?1", assignedTo.value())
            .map(entities -> entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList()))
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<List<Lead>> findActiveLeads() {
    return Panache.withSession(() -> LeadEntity.<LeadEntity>list("active = true")
            .map(entities -> entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList()))
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<List<Lead>> findQualifiedLeads() {
    return Panache.withSession(() -> LeadEntity.<LeadEntity>list("status in ?1", 
                List.of(LeadStatus.QUALIFIED, LeadStatus.NURTURING))
            .map(entities -> entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList()))
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<List<Lead>> findByScoreGreaterThan(double score) {
    return Panache.withSession(() -> LeadEntity.<LeadEntity>list("score >= ?1", score)
            .map(entities -> entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList()))
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<Long> countByStatus(LeadStatus status) {
    return Panache.withSession(() -> LeadEntity.count("status = ?1", status)
            ).subscribe()
            .asCompletionStage();
    }
}