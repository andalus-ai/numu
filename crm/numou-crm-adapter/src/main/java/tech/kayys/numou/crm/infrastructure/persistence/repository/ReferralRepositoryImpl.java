package tech.kayys.numou.crm.infrastructure.persistence.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.tenant.TenantScope;
import tech.kayys.numou.crm.application.tenant.TenantScopeProvider;
import tech.kayys.numou.crm.domain.referral.Referral;
import tech.kayys.numou.crm.domain.referral.ReferralId;
import tech.kayys.numou.crm.domain.repository.ReferralRepository;
import tech.kayys.numou.crm.infrastructure.persistence.entity.AccountEntity;
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

        return Panache.withTransaction(() -> AccountEntity.count(
                        "id = ?1 and tenantId = ?2", mapped.referrerAccountId, tenant)
                .flatMap(count -> count == 1
                        ? Uni.createFrom().voidItem()
                        : Uni.createFrom().failure(new SecurityException(
                                "Referrer account does not belong to the verified tenant")))
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
