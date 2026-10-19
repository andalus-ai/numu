package tech.kayys.numou.crm.infrastructure.persistence.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import tech.kayys.numou.crm.domain.account.Account;
import tech.kayys.numou.crm.domain.identifier.AccountId;
import tech.kayys.numou.crm.domain.repository.AccountRepository;
import tech.kayys.numou.crm.infrastructure.persistence.entity.AccountEntity;
import tech.kayys.numou.crm.infrastructure.persistence.mapper.AccountMapper;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class AccountRepositoryImpl implements AccountRepository {

    private final AccountMapper mapper;

    public AccountRepositoryImpl(AccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CompletionStage<Account> save(Account account) {
        AccountEntity entity = mapper.toEntity(account);
        return Panache.withTransaction(() -> entity.<AccountEntity>persist()
                .map(ignored -> account))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<Account>> findById(AccountId id) {
    return Panache.withSession(() -> AccountEntity.<AccountEntity>findById(id.getValue())
                .map(entity -> entity == null
                        ? Optional.<Account>empty() : Optional.of(mapper.toDomain(entity)))
                ).subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsById(AccountId id) {
    return Panache.withSession(() -> AccountEntity.findById(id.getValue())
                .map(entity -> entity != null)
                ).subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Void> delete(Account account) {
        return deleteById(account.id());
    }

    @Override
    public CompletionStage<Void> deleteById(AccountId id) {
    return Panache.withTransaction(() -> AccountEntity.deleteById(id.getValue())
                .map(ignored -> (Void) null)
                ).subscribe().asCompletionStage();
    }
}
