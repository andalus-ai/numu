package tech.kayys.numou.crm.infrastructure.persistence.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.tenant.TenantScopeProvider;
import tech.kayys.numou.crm.domain.identifier.CustomerId;
import tech.kayys.numou.crm.domain.identifier.OpportunityId;
import tech.kayys.numou.crm.domain.model.Opportunity;
import tech.kayys.numou.crm.domain.repository.OpportunityRepository;
import tech.kayys.numou.crm.domain.valueobject.OpportunityStage;
import tech.kayys.numou.crm.infrastructure.persistence.entity.CustomerEntity;
import tech.kayys.numou.crm.infrastructure.persistence.entity.OpportunityEntity;
import tech.kayys.numou.crm.infrastructure.persistence.mapper.OpportunityMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Tenant-scoped CRM opportunity persistence adapter. Customer relationships
 * are validated against the same verified tenant before an opportunity is saved.
 */
@ApplicationScoped
public class OpportunityRepositoryImpl implements OpportunityRepository {

    private final OpportunityMapper mapper;
    private final TenantScopeProvider tenantScopeProvider;

    @Inject
    public OpportunityRepositoryImpl(OpportunityMapper mapper, TenantScopeProvider tenantScopeProvider) {
        this.mapper = mapper;
        this.tenantScopeProvider = tenantScopeProvider;
    }

    private UUID tenantId() {
        return tenantScopeProvider.requireTenantScope().tenantId().value();
    }

    @Override
    public CompletionStage<Opportunity> save(Opportunity opportunity) {
        UUID tenant = tenantId();
        OpportunityEntity entity = mapper.toEntity(opportunity);
        entity.tenantId = tenant;

        return Panache.withTransaction(() -> {
            Uni<Long> customerCheck = entity.customerId == null
                ? Uni.createFrom().item(1L)
                : CustomerEntity.count("id = ?1 and tenantId = ?2", entity.customerId, tenant);
            return customerCheck.flatMap(count -> {
                if (count == 0) {
                    return Uni.createFrom().failure(
                        new SecurityException("CRM opportunity customer must belong to the verified tenant"));
                }
                if (entity.id == null) {
                    entity.id = UUID.randomUUID();
                }
                return OpportunityEntity.<OpportunityEntity>find(
                        "id = ?1 and tenantId = ?2", entity.id, tenant)
                    .firstResult()
                    .flatMap(existing -> {
                        if (existing == null) {
                            // A globally colliding foreign ID fails on the primary key; it is never updated.
                            return entity.<OpportunityEntity>persist().map(saved -> {
                                opportunity.clearEvents();
                                return opportunity;
                            });
                        }
                        copyFields(entity, existing);
                        opportunity.clearEvents();
                        return Uni.createFrom().item(opportunity);
                    });
            });
        }).subscribe().asCompletionStage();
    }

    private static void copyFields(OpportunityEntity source, OpportunityEntity target) {
        target.name = source.name;
        target.description = source.description;
        target.customerId = source.customerId;
        target.customerName = source.customerName;
        target.stage = source.stage;
        target.estimatedValue = source.estimatedValue;
        target.probability = source.probability;
        target.weightedValue = source.weightedValue;
        target.currencyCode = source.currencyCode;
        target.assignedTo = source.assignedTo;
        target.expectedCloseDate = source.expectedCloseDate;
        target.leadSource = source.leadSource;
        target.productInterest = source.productInterest;
        target.competitors = source.competitors;
        target.decisionCriteria = source.decisionCriteria;
        target.nextStep = source.nextStep;
        target.notes = source.notes;
        target.activities = source.activities;
        target.active = source.active;
        target.updatedAt = source.updatedAt;
    }

    @Override
    public CompletionStage<Optional<Opportunity>> findById(OpportunityId id) {
        UUID tenant = tenantId();
        return Panache.withSession(() -> OpportunityEntity.<OpportunityEntity>find(
                "id = ?1 and tenantId = ?2", id.getValue(), tenant)
            .firstResult()
            .map(entity -> entity == null ? Optional.<Opportunity>empty() : Optional.of(mapper.toDomain(entity)))
        ).subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsById(OpportunityId id) {
        UUID tenant = tenantId();
        return Panache.withSession(() -> OpportunityEntity.count(
                "id = ?1 and tenantId = ?2", id.getValue(), tenant)
            .map(count -> count > 0)
        ).subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Void> delete(Opportunity opportunity) {
        return deleteById(opportunity.getId());
    }

    @Override
    public CompletionStage<Void> deleteById(OpportunityId id) {
        UUID tenant = tenantId();
        return Panache.withTransaction(() -> OpportunityEntity.delete(
                "id = ?1 and tenantId = ?2", id.getValue(), tenant)
            .map(deleted -> (Void) null)
        ).subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<List<Opportunity>> findByCustomerId(CustomerId customerId) {
        UUID tenant = tenantId();
        return list("tenantId = ?1 and customerId = ?2", tenant, customerId.getValue());
    }

    @Override
    public CompletionStage<List<Opportunity>> findByStage(OpportunityStage stage) {
        UUID tenant = tenantId();
        return list("tenantId = ?1 and stage = ?2", tenant, stage);
    }

    @Override
    public CompletionStage<List<Opportunity>> findActiveOpportunities() {
        UUID tenant = tenantId();
        return list("tenantId = ?1 and active = true", tenant);
    }

    private CompletionStage<List<Opportunity>> list(String query, Object... parameters) {
        return Panache.withSession(() -> OpportunityEntity.<OpportunityEntity>list(query, parameters)
            .map(entities -> entities.stream().map(mapper::toDomain).collect(Collectors.toList()))
        ).subscribe().asCompletionStage();
    }
}
