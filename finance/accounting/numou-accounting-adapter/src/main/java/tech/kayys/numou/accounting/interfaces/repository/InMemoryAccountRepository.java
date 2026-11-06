package tech.kayys.numou.accounting.interfaces.repository;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.accounting.application.port.AccountRepository;
import tech.kayys.numou.accounting.domain.identifier.AccountId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.model.Account;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class InMemoryAccountRepository implements AccountRepository {
    private final Map<AccountId, Account> store = new ConcurrentHashMap<>();

    @Override
    public Uni<Optional<Account>> findById(TenantRef tenantId, LedgerId ledgerId, AccountId id) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ledgerId, "ledgerId");
        Objects.requireNonNull(id, "id");
        return Uni.createFrom().item(Optional.ofNullable(store.get(id))
                .filter(account -> inScope(account, tenantId, ledgerId)));
    }

    @Override
    public Uni<Optional<Account>> findByAccountNumber(TenantRef tenantId, LedgerId ledgerId, String accountNumber) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ledgerId, "ledgerId");
        Objects.requireNonNull(accountNumber, "accountNumber");
        return Uni.createFrom().item(store.values().stream()
                .filter(account -> inScope(account, tenantId, ledgerId))
                .filter(account -> account.getAccountNumber().equalsIgnoreCase(accountNumber))
                .findFirst());
    }

    @Override
    public Uni<List<Account>> findAll(TenantRef tenantId, LedgerId ledgerId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ledgerId, "ledgerId");
        return Uni.createFrom().item(store.values().stream()
                .filter(account -> inScope(account, tenantId, ledgerId))
                .toList());
    }

    /**
     * Compatibility-only unscoped lookup. Do not use from tenant-facing code.
     */
    @Override
    @Deprecated(forRemoval = true)
    public Uni<Optional<Account>> findById(AccountId id) {
        return Uni.createFrom().item(Optional.ofNullable(store.get(id)));
    }

    /**
     * Compatibility-only unscoped lookup. Do not use from tenant-facing code.
     */
    @Override
    @Deprecated(forRemoval = true)
    public Uni<Optional<Account>> findByAccountNumber(String accountNumber) {
        return Uni.createFrom().item(store.values().stream()
                .filter(account -> account.getAccountNumber().equalsIgnoreCase(accountNumber))
                .findFirst());
    }

    /**
     * Compatibility-only unscoped lookup. Do not use from tenant-facing code.
     */
    @Override
    @Deprecated(forRemoval = true)
    public Uni<List<Account>> findAll() {
        return Uni.createFrom().item(new ArrayList<>(store.values()));
    }

    @Override
    public Uni<Void> save(Account account) {
        Objects.requireNonNull(account, "account");
        store.put(account.id(), account);
        return Uni.createFrom().voidItem();
    }

    private static boolean inScope(Account account, TenantRef tenantId, LedgerId ledgerId) {
        return account.tenantId().equals(tenantId) && account.ledgerId().equals(ledgerId);
    }
}
