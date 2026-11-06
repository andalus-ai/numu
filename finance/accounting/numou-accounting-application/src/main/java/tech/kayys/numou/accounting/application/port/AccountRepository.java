package tech.kayys.numou.accounting.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.accounting.domain.identifier.AccountId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.model.Account;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;

import java.util.List;
import java.util.Optional;

/**
 * Persistence boundary for chart-of-accounts aggregates.
 *
 * <p>Tenant and ledger scope are part of every query contract. The deprecated
 * unscoped methods remain temporarily for source compatibility with existing
 * application services; new API and persistence code must use scoped methods.
 */
public interface AccountRepository {
    Uni<Optional<Account>> findById(TenantRef tenantId, LedgerId ledgerId, AccountId id);
    Uni<Optional<Account>> findByAccountNumber(TenantRef tenantId, LedgerId ledgerId, String accountNumber);
    Uni<List<Account>> findAll(TenantRef tenantId, LedgerId ledgerId);

    /** @deprecated Use {@link #findById(TenantRef, LedgerId, AccountId)}. */
    @Deprecated(forRemoval = true)
    Uni<Optional<Account>> findById(AccountId id);

    /** @deprecated Use {@link #findByAccountNumber(TenantRef, LedgerId, String)}. */
    @Deprecated(forRemoval = true)
    Uni<Optional<Account>> findByAccountNumber(String accountNumber);

    /** @deprecated Use {@link #findAll(TenantRef, LedgerId)}. */
    @Deprecated(forRemoval = true)
    Uni<List<Account>> findAll();

    Uni<Void> save(Account account);
}
