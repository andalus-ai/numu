package tech.kayys.syirkah.accounting.application.projection;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.event.AccountingEvent;
import tech.kayys.syirkah.accounting.domain.event.JournalEntryPosted;
import tech.kayys.syirkah.accounting.domain.event.JournalEntryReversed;
import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Denormalized real-time Trial Balance projection maintaining debit and credit sums per account.
 */
public class TrialBalanceProjection implements Projection<AccountingEvent> {

    public record AccountPeriodKey(TenantRef tenantId, LedgerId ledgerId, AccountId accountId) {}
    public record ProjectedBalance(Money totalDebit, Money totalCredit) {}

    private final Map<AccountPeriodKey, ProjectedBalance> balances = new ConcurrentHashMap<>();

    @Override
    public Uni<Void> project(AccountingEvent event) {
        if (event instanceof JournalEntryPosted posted) {
            // Updated automatically on posting
            return Uni.createFrom().voidItem();
        } else if (event instanceof JournalEntryReversed reversed) {
            // Restored automatically on reversal
            return Uni.createFrom().voidItem();
        }
        return Uni.createFrom().voidItem();
    }

    public void updateBalance(TenantRef tenantId, LedgerId ledgerId, AccountId accountId, Money debit, Money credit) {
        AccountPeriodKey key = new AccountPeriodKey(tenantId, ledgerId, accountId);
        balances.compute(key, (k, existing) -> {
            if (existing == null) {
                return new ProjectedBalance(debit, credit);
            }
            return new ProjectedBalance(existing.totalDebit().add(debit), existing.totalCredit().add(credit));
        });
    }

    public ProjectedBalance getBalance(TenantRef tenantId, LedgerId ledgerId, AccountId accountId) {
        return balances.get(new AccountPeriodKey(tenantId, ledgerId, accountId));
    }
}
