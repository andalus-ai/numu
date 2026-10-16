package tech.kayys.syirkah.accounting.interfaces.repository;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.syirkah.accounting.application.port.AccountRepository;
import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.model.Account;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class InMemoryAccountRepository implements AccountRepository {
    private final Map<AccountId, Account> store = new ConcurrentHashMap<>();

    @Override
    public Uni<Optional<Account>> findById(AccountId id) {
        return Uni.createFrom().item(Optional.ofNullable(store.get(id)));
    }

    @Override
    public Uni<Optional<Account>> findByAccountNumber(String accountNumber) {
        return Uni.createFrom().item(
                store.values().stream()
                        .filter(a -> a.getAccountNumber().equalsIgnoreCase(accountNumber))
                        .findFirst()
        );
    }

    @Override
    public Uni<List<Account>> findAll() {
        return Uni.createFrom().item(new ArrayList<>(store.values()));
    }

    @Override
    public Uni<Void> save(Account account) {
        store.put(account.id(), account);
        return Uni.createFrom().voidItem();
    }
}
