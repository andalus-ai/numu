package tech.kayys.numou.crm.infrastructure.persistence.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.tenant.TenantScope;
import tech.kayys.numou.crm.application.tenant.TenantScopeProvider;
import tech.kayys.numou.crm.domain.account.Account;
import tech.kayys.numou.crm.domain.identifier.AccountId;
import tech.kayys.numou.crm.domain.repository.AccountRepository;
import tech.kayys.numou.crm.infrastructure.persistence.entity.AccountEntity;
import tech.kayys.numou.crm.infrastructure.persistence.mapper.AccountMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Account persistence adapter. Tenant ownership is resolved only from the
 * trusted runtime scope; callers cannot choose or override tenant ownership.
 */
@ApplicationScoped
public class AccountRepositoryImpl implements AccountRepository {

    private final AccountMapper mapper;
    private final TenantScopeProvider tenantScopeProvider;

    @Inject
    public AccountRepositoryImpl(AccountMapper mapper, TenantScopeProvider tenantScopeProvider) {
        this.mapper = mapper;
        this.tenantScopeProvider = tenantScopeProvider;
    }

    private UUID tenantId() {
        TenantScope scope = tenantScopeProvider.requireTenantScope();
        return scope.tenantId().value();
    }

    @Override
    public CompletionStage<Account> save(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("account is required");
        }
        UUID tenant = tenantId();
        AccountEntity mapped = mapper.toEntity(account);
        mapped.tenantId = tenant;

        return Panache.withTransaction(() -> validateParent(mapped.parentAccountId, tenant)
                .flatMap(ignored -> AccountEntity.<AccountEntity>find(
                        "id = ?1 and tenantId = ?2", mapped.id, tenant).firstResult())
                .flatMap(existing -> {
                    if (existing == null) {
                        // A foreign-tenant ID can only collide with the global PK;
                        // it is never loaded or overwritten by this operation.
                        return mapped.<AccountEntity>persist().map(saved -> account);
                    }
                    if (!existing.participantId.equals(mapped.participantId)) {
                        return Uni.createFrom().failure(new SecurityException(
                                "An account cannot be reassigned to a different participant"));
                    }
                    copyFields(mapped, existing);
                    return Uni.createFrom().item(account);
                }))
                .subscribe().asCompletionStage();
    }

    private Uni<Void> validateParent(UUID parentAccountId, UUID tenant) {
        if (parentAccountId == null) {
            return Uni.createFrom().voidItem();
        }
        return AccountEntity.count("id = ?1 and tenantId = ?2", parentAccountId, tenant)
                .flatMap(count -> count == 1
                        ? Uni.createFrom().voidItem()
                        : Uni.createFrom().failure(new SecurityException(
                                "Parent account does not belong to the verified tenant")));
    }

    private static void copyFields(AccountEntity source, AccountEntity target) {
        target.parentAccountId = source.parentAccountId;
        target.name = source.name;
        target.status = source.status;
        target.roles = source.roles;
        target.active = source.active;
        target.updatedAt = source.updatedAt;
    }

    @Override
    public CompletionStage<Optional<Account>> findById(AccountId id) {
        UUID tenant = tenantId();
        return Panache.withSession(() -> AccountEntity.<AccountEntity>find(
                        "id = ?1 and tenantId = ?2", id.getValue(), tenant)
                .firstResult()
                .map(entity -> entity == null
                        ? Optional.<Account>empty() : Optional.of(mapper.toDomain(entity))))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsById(AccountId id) {
        UUID tenant = tenantId();
        return Panache.withSession(() -> AccountEntity.count(
                        "id = ?1 and tenantId = ?2", id.getValue(), tenant)
                .map(count -> count > 0))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Void> delete(Account account) {
        if (account == null || account.id() == null) {
            throw new IllegalArgumentException("account ID is required");
        }
        return deleteById(account.id());
    }

    @Override
    public CompletionStage<Void> deleteById(AccountId id) {
        UUID tenant = tenantId();
        return Panache.withTransaction(() -> AccountEntity.delete(
                        "id = ?1 and tenantId = ?2", id.getValue(), tenant)
                .map(ignored -> (Void) null))
                .subscribe().asCompletionStage();
    }
}
