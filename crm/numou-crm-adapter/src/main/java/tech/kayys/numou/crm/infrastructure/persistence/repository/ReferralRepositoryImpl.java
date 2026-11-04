package tech.kayys.numou.crm.infrastructure.persistence.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.tenant.TenantScope;
import tech.kayys.numou.crm.application.tenant.TenantScopeProvider;
import tech.kayys.numou.crm.domain.referral.Referral;
import tech.kayys.numou.crm.domain.referral.ReferralId;
import tech.kayys.numou.crm.domain.referral.ReferralTarget;
import tech.kayys.numou.crm.domain.repository.ReferralRepository;
import tech.kayys.numou.crm.infrastructure.persistence.entity.AccountEntity;
import tech.kayys.numou.crm.infrastructure.persistence.entity.LeadEntity;
import tech.kayys.numou.crm.infrastructure.persistence.entity.OpportunityEntity;
import tech.kayys.numou.crm.infrastructure.persistence.entity.ReferralEntity;
import tech.kayys.numou.crm.infrastructure.persistence.mapper.ReferralMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Referral persistence adapter. Tenant identity is resolved from verified
 * runtime context and applied to every persistence operation.
 */
@ApplicationScoped
public class ReferralRepositoryImpl implements ReferralRepository {

    private final ReferralMapper mapper;
    private final TenantScopeProvider tenantScopeProvider;

    @Inject
    public ReferralRepositoryImpl(ReferralMapper mapper, TenantScopeProvider tenantScopeProvider) {
        this.mapper = mapper;
        this.tenantScopeProvider = tenantScopeProvider;
    }

    private UUID tenantId() {
        TenantScope scope = tenantScopeProvider.requireTenantScope();
        return scope.tenantId().value();
    }

    @Override
    public CompletionStage<Referral> save(Referral referral) {
        if (referral == null || referral.id() == null) {
            throw new IllegalArgumentException("referral and referral ID are required");
        }
        UUID tenant = tenantId();
        ReferralEntity mapped = mapper.toEntity(referral);
        mapped.tenantId = tenant;

        return Panache.withTransaction(() -> validateReferences(mapped, tenant)
                .flatMap(ignored -> ReferralEntity.<ReferralEntity>find(
                        "id = ?1 and tenantId = ?2", mapped.id, tenant).firstResult())
                .flatMap(existing -> {
                    if (existing == null) {
                        // A foreign-tenant ID can only collide with the global PK;
                        // it is never loaded or overwritten.
                        return mapped.<ReferralEntity>persist().map(saved -> referral);
                    }
                    if (!existing.referrerAccountId.equals(mapped.referrerAccountId)) {
                        return Uni.createFrom().failure(new SecurityException(
                                "A referral cannot be reassigned to another referrer account"));
                    }
                    copyFields(mapped, existing);
                    return Uni.createFrom().item(referral);
                })
                .map(saved -> {
                    referral.clearEvents();
                    return saved;
                }))
                .subscribe().asCompletionStage();
    }

    private Uni<Void> validateReferences(ReferralEntity referral, UUID tenant) {
        return AccountEntity.count("id = ?1 and tenantId = ?2", referral.referrerAccountId, tenant)
                .flatMap(accountCount -> {
                    if (accountCount != 1) {
                        return Uni.createFrom().failure(new SecurityException(
                                "Referrer account does not belong to the verified tenant"));
                    }
                    if (referral.target == ReferralTarget.LEAD) {
                        if (referral.targetOpportunityId != null) {
                            return Uni.createFrom().failure(new IllegalArgumentException(
                                    "A lead referral cannot reference an opportunity"));
                        }
                        return referral.targetLeadId == null
                                ? Uni.createFrom().voidItem()
                                : LeadEntity.count("id = ?1 and tenantId = ?2", referral.targetLeadId, tenant)
                                    .flatMap(count -> count == 1
                                            ? Uni.createFrom().voidItem()
                                            : Uni.createFrom().failure(new SecurityException(
                                                    "Target lead does not belong to the verified tenant")));
                    }
                    if (referral.target == ReferralTarget.OPPORTUNITY) {
                        if (referral.targetLeadId != null) {
                            return Uni.createFrom().failure(new IllegalArgumentException(
                                    "An opportunity referral cannot reference a lead"));
                        }
                        return referral.targetOpportunityId == null
                                ? Uni.createFrom().voidItem()
                                : OpportunityEntity.count("id = ?1 and tenantId = ?2",
                                        referral.targetOpportunityId, tenant)
                                    .flatMap(count -> count == 1
                                            ? Uni.createFrom().voidItem()
                                            : Uni.createFrom().failure(new SecurityException(
                                                    "Target opportunity does not belong to the verified tenant")));
                    }
                    return Uni.createFrom().failure(new IllegalArgumentException(
                            "Unsupported referral target"));
                });
    }

    private static void copyFields(ReferralEntity source, ReferralEntity target) {
        target.target = source.target;
        target.type = source.type;
        target.status = source.status;
        target.code = source.code;
        target.description = source.description;
        target.referredAt = source.referredAt;
        target.acceptedAt = source.acceptedAt;
        target.convertedAt = source.convertedAt;
        target.targetLeadId = source.targetLeadId;
        target.targetOpportunityId = source.targetOpportunityId;
        target.externalReference = source.externalReference;
        target.active = source.active;
        target.updatedAt = source.updatedAt;
    }

    @Override
    public CompletionStage<Optional<Referral>> findById(ReferralId id) {
        UUID tenant = tenantId();
        return Panache.withSession(() -> ReferralEntity.<ReferralEntity>find(
                        "id = ?1 and tenantId = ?2", id.getValue(), tenant)
                .firstResult()
                .map(entity -> entity == null
                        ? Optional.<Referral>empty() : Optional.of(mapper.toDomain(entity))))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsById(ReferralId id) {
        UUID tenant = tenantId();
        return Panache.withSession(() -> ReferralEntity.count(
                        "id = ?1 and tenantId = ?2", id.getValue(), tenant)
                .map(count -> count > 0))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Void> delete(Referral referral) {
        if (referral == null || referral.id() == null) {
            throw new IllegalArgumentException("referral ID is required");
        }
        return deleteById(referral.id());
    }

    @Override
    public CompletionStage<Void> deleteById(ReferralId id) {
        UUID tenant = tenantId();
        return Panache.withTransaction(() -> ReferralEntity.delete(
                        "id = ?1 and tenantId = ?2", id.getValue(), tenant)
                .map(deleted -> (Void) null))
                .subscribe().asCompletionStage();
    }
}
