package tech.kayys.numou.crm.infrastructure.persistence.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.tenant.TenantScopeProvider;
import tech.kayys.numou.crm.domain.identifier.LeadId;
import tech.kayys.numou.crm.domain.model.Lead;
import tech.kayys.numou.crm.domain.repository.LeadRepository;
import tech.kayys.numou.crm.domain.valueobject.LeadStatus;
import tech.kayys.numou.crm.infrastructure.persistence.entity.LeadEntity;
import tech.kayys.numou.crm.infrastructure.persistence.mapper.LeadMapper;
import tech.kayys.numou.identity.domain.user.UserId;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Tenant-scoped implementation of the CRM lead repository.
 *
 * The tenant is resolved only from the verified shared request context. The
 * lead activity element collection is protected by loading/deleting its
 * parent lead through the same tenant predicate.
 */
@ApplicationScoped
public class LeadRepositoryImpl implements LeadRepository {

    private final LeadMapper mapper;
    private final TenantScopeProvider tenantScopeProvider;

    @Inject
    public LeadRepositoryImpl(LeadMapper mapper, TenantScopeProvider tenantScopeProvider) {
        this.mapper = mapper;
        this.tenantScopeProvider = tenantScopeProvider;
    }

    private UUID tenantId() {
        return tenantScopeProvider.requireTenantScope().tenantId().value();
    }

    @Override
    public CompletionStage<Lead> save(Lead lead) {
        UUID tenantId = tenantId();
        LeadEntity entity = mapper.toEntity(lead);
        entity.tenantId = tenantId;
        if (entity.id == null) {
            entity.id = UUID.randomUUID();
        }

        return Panache.withTransaction(() -> {
            if (entity.id == null) {
                entity.id = UUID.randomUUID();
            }
            return LeadEntity.<LeadEntity>find("id = ?1 and tenantId = ?2", entity.id, tenantId)
                .firstResult()
                .flatMap(existing -> {
                    if (existing == null) {
                        // A foreign tenant's ID can never be updated; a global PK collision fails closed.
                        return entity.<LeadEntity>persist().map(saved -> {
                            lead.clearEvents();
                            return lead;
                        });
                    }
                    copyLeadFields(entity, existing);
                    lead.clearEvents();
                    return Uni.createFrom().item(lead);
                });
        }).subscribe().asCompletionStage();
    }

    private static void copyLeadFields(LeadEntity source, LeadEntity target) {
        target.firstName = source.firstName;
        target.lastName = source.lastName;
        target.email = source.email;
        target.phone = source.phone;
        target.company = source.company;
        target.jobTitle = source.jobTitle;
        target.industry = source.industry;
        target.source = source.source;
        target.status = source.status;
        target.assignedTo = source.assignedTo;
        target.notes = source.notes;
        target.score = source.score;
        target.activities = source.activities;
        target.active = source.active;
        target.updatedAt = source.updatedAt;
    }

    @Override
    public CompletionStage<Optional<Lead>> findById(LeadId id) {
        UUID tenantId = tenantId();
        return Panache.withSession(() -> LeadEntity.<LeadEntity>find(
                "id = ?1 and tenantId = ?2", id.getValue(), tenantId)
            .firstResult()
            .map(entity -> entity == null ? Optional.<Lead>empty() : Optional.of(mapper.toDomain(entity)))
        ).subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsById(LeadId id) {
        UUID tenantId = tenantId();
        return Panache.withSession(() -> LeadEntity.count(
                "id = ?1 and tenantId = ?2", id.getValue(), tenantId)
            .map(count -> count > 0)
        ).subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Void> delete(Lead lead) {
        return deleteById(lead.getId());
    }

    @Override
    public CompletionStage<Void> deleteById(LeadId id) {
        UUID tenantId = tenantId();
        return Panache.withTransaction(() -> LeadEntity.delete(
                "id = ?1 and tenantId = ?2", id.getValue(), tenantId)
            .map(deleted -> (Void) null)
        ).subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<List<Lead>> findByStatus(LeadStatus status) {
        UUID tenantId = tenantId();
        return list("tenantId = ?1 and status = ?2", tenantId, status);
    }

    @Override
    public CompletionStage<List<Lead>> findByEmail(String email) {
        UUID tenantId = tenantId();
        return list("tenantId = ?1 and email = ?2", tenantId, email);
    }

    @Override
    public CompletionStage<List<Lead>> findByAssignedTo(UserId assignedTo) {
        UUID tenantId = tenantId();
        return list("tenantId = ?1 and assignedTo = ?2", tenantId, assignedTo.value());
    }

    @Override
    public CompletionStage<List<Lead>> findActiveLeads() {
        UUID tenantId = tenantId();
        return list("tenantId = ?1 and active = true", tenantId);
    }

    @Override
    public CompletionStage<List<Lead>> findQualifiedLeads() {
        UUID tenantId = tenantId();
        return list("tenantId = ?1 and status in ?2",
                tenantId, List.of(LeadStatus.QUALIFIED, LeadStatus.NURTURING));
    }

    @Override
    public CompletionStage<List<Lead>> findByScoreGreaterThan(double score) {
        UUID tenantId = tenantId();
        return list("tenantId = ?1 and score >= ?2", tenantId, score);
    }

    @Override
    public CompletionStage<Long> countByStatus(LeadStatus status) {
        UUID tenantId = tenantId();
        return Panache.withSession(() -> LeadEntity.count(
                "tenantId = ?1 and status = ?2", tenantId, status))
            .subscribe().asCompletionStage();
    }

    private CompletionStage<List<Lead>> list(String query, Object... parameters) {
        return Panache.withSession(() -> LeadEntity.<LeadEntity>list(query, parameters)
            .map(entities -> entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList()))
        ).subscribe().asCompletionStage();
    }
}
