package tech.kayys.numou.numouadapter.ledger;

import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.model.Account;
import tech.kayys.numou.accounting.domain.valueobject.AccountType;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numouapplication.SyirkahAccountMap;
import tech.kayys.numou.numouapplication.port.BusinessAssetsPort;
import tech.kayys.numou.numoudomain.BookStatements;
import tech.kayys.numou.numoudomain.SyirkahId;
import tech.kayys.numou.numoudomain.SyirkahPostingPlan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Reads a syirkah's assets and debts from the accounting module, where each syirkah has a dedicated ledger.
 * Only accounts of that ledger count. The syirkah's own role accounts (cash excepted, because cash is a real asset)
 * are left out: partner capital, profit payable and period result are equity, and partner receivables are added by
 * the balance sheet itself. Balances are the accounts' current balances; the accounting module keeps no history, so
 * the as-of date of the statement is the date it is requested. Every asset is shown as current and every debt as
 * short term, because accounts carry no maturity.
 */
public class AccountingLedgerAssets implements BusinessAssetsPort {

    private final Supplier<List<Account>> accounts;
    private final Function<SyirkahId, LedgerId> ledgerOf;
    private final Set<String> equityCodes = new HashSet<>();

    public AccountingLedgerAssets(Supplier<List<Account>> accounts, Function<SyirkahId, LedgerId> ledgerOf,
                                  SyirkahAccountMap map) {
        this.accounts = Objects.requireNonNull(accounts);
        this.ledgerOf = Objects.requireNonNull(ledgerOf);
        Objects.requireNonNull(map);
        for (SyirkahPostingPlan.AccountRole r : SyirkahPostingPlan.AccountRole.values()) {
            if (r != SyirkahPostingPlan.AccountRole.CASH) {
                equityCodes.add(map.codeOf(r));
            }
        }
    }

    @Override
    public Optional<Snapshot> forSyirkah(TenantId tenant, SyirkahId syirkah) {
        LedgerId ledger = ledgerOf.apply(syirkah);
        if (ledger == null) {
            return Optional.empty();
        }
        List<BookStatements.AssetLine> assets = new ArrayList<>();
        List<BookStatements.LiabilityLine> liabilities = new ArrayList<>();
        boolean any = false;
        for (Account a : accounts.get()) {
            if (!ledger.equals(a.ledgerId())) {
                continue;
            }
            any = true;
            if (equityCodes.contains(a.getAccountNumber()) || !a.isActive()
                    || a.getCurrentBalance().isZero()) {
                continue;
            }
            String label = a.getAccountNumber() + " " + a.getName();
            if (a.getAccountType() == AccountType.ASSET) {
                assets.add(new BookStatements.AssetLine(BookStatements.AssetGroup.CURRENT, label,
                        a.getCurrentBalance()));
            } else if (a.getAccountType() == AccountType.LIABILITY) {
                liabilities.add(new BookStatements.LiabilityLine(BookStatements.LiabilityGroup.SHORT_TERM, label,
                        a.getCurrentBalance()));
            }
        }
        return any ? Optional.of(new Snapshot(List.copyOf(assets), List.copyOf(liabilities))) : Optional.empty();
    }
}
