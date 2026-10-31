package tech.kayys.numou.numouadapter;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.accounting.domain.identifier.AccountId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.model.Account;
import tech.kayys.numou.accounting.domain.valueobject.AccountType;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.numouadapter.ledger.AccountingLedgerAssets;
import tech.kayys.numou.numouapplication.SyirkahAccountMap;
import tech.kayys.numou.numoudomain.SyirkahId;
import tech.kayys.numou.numoudomain.SyirkahPostingPlan.AccountRole;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AccountingLedgerAssetsTest {

    private static final Currency IDR = Currency.of("IDR");
    private static final LedgerId MINE = LedgerId.of("syirkah-1");
    private static final LedgerId OTHER = LedgerId.of("syirkah-2");

    private final SyirkahAccountMap map = new SyirkahAccountMap(Map.of(AccountRole.CASH, "1100",
            AccountRole.PARTNER_CAPITAL, "3100", AccountRole.PROFIT_PAYABLE, "2300",
            AccountRole.PERIOD_RESULT, "3900", AccountRole.PARTNER_RECEIVABLE, "1300"));

    private static Account account(LedgerId l, String no, String name, AccountType t, long balance) {
        Account a = new Account(AccountId.generate(), null, l, no, name, t, IDR);
        a.credit(Money.of(BigDecimal.ZERO, IDR));
        if (balance != 0) {
            if (t.isNormalDebitBalance()) {
                a.debit(Money.of(BigDecimal.valueOf(balance), IDR));
            } else {
                a.credit(Money.of(BigDecimal.valueOf(balance), IDR));
            }
        }
        return a;
    }

    @Test
    void onlyThisSyirkahsAssetsAndDebtsAreReturnedAndEquityAccountsAreLeftOut() {
        List<Account> all = new ArrayList<>(List.of(
                account(MINE, "1100", "Cash", AccountType.ASSET, 50),
                account(MINE, "1200", "Stock", AccountType.ASSET, 70),
                account(MINE, "1300", "Partner receivable", AccountType.ASSET, 5),
                account(MINE, "2100", "Suppliers", AccountType.LIABILITY, 20),
                account(MINE, "2300", "Profit payable", AccountType.LIABILITY, 9),
                account(MINE, "3100", "Capital", AccountType.EQUITY, 100),
                account(MINE, "1900", "Unused", AccountType.ASSET, 0),
                account(OTHER, "1100", "Cash of another", AccountType.ASSET, 999)));
        var port = new AccountingLedgerAssets(() -> all, id -> MINE, map);
        var snap = port.forSyirkah(TenantId.generate(), SyirkahId.generate()).orElseThrow();
        assertEquals(List.of("1100 Cash", "1200 Stock"),
                snap.assets().stream().map(x -> x.label()).toList());
        assertEquals(List.of("2100 Suppliers"), snap.liabilities().stream().map(x -> x.label()).toList());
        assertEquals(0, BigDecimal.valueOf(120).compareTo(snap.assets().stream()
                .map(x -> x.amount().amount()).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    @Test
    void aSyirkahWithoutALedgerOrWithoutAccountsHasNoSnapshot() {
        assertTrue(new AccountingLedgerAssets(List::of, id -> null, map)
                .forSyirkah(TenantId.generate(), SyirkahId.generate()).isEmpty());
        assertTrue(new AccountingLedgerAssets(List::of, id -> MINE, map)
                .forSyirkah(TenantId.generate(), SyirkahId.generate()).isEmpty());
    }
}
