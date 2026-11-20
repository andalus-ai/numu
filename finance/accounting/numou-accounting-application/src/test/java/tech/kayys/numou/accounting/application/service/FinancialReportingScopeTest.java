package tech.kayys.numou.accounting.application.service;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.accounting.application.port.AccountRepository;
import tech.kayys.numou.accounting.domain.identifier.AccountId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.model.Account;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;
import tech.kayys.numou.accounting.domain.valueobject.AccountType;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FinancialReportingScopeTest {

    @Test
    void trialBalanceIncludesOnlyAccountsFromRequestedTenantAndLedger() {
        TenantRef tenantA = TenantRef.of("tenant-a");
        TenantRef tenantB = TenantRef.of("tenant-b");
        LedgerId primary = LedgerId.primary();
        LedgerId tax = LedgerId.of("tax");
        Currency usd = Currency.of("USD");

        Account cash = account(tenantA, primary, "1000", AccountType.ASSET, usd, true);
        Account capital = account(tenantA, primary, "3000", AccountType.EQUITY, usd, false);
        Account otherTenant = account(tenantB, primary, "1000", AccountType.ASSET, usd, true);
        Account otherLedger = account(tenantA, tax, "1000", AccountType.ASSET, usd, true);

        AccountRepository repository = new AccountRepository() {
            private final List<Account> all = List.of(cash, capital, otherTenant, otherLedger);

            @Override
            public Uni<Optional<Account>> findById(TenantRef tenantId, LedgerId ledgerId, AccountId id) {
                return Uni.createFrom().item(all.stream()
                        .filter(a -> a.tenantId().equals(tenantId) && a.ledgerId().equals(ledgerId))
                        .filter(a -> a.id().equals(id)).findFirst());
            }

            @Override
            public Uni<Optional<Account>> findByAccountNumber(TenantRef tenantId, LedgerId ledgerId, String number) {
                return Uni.createFrom().item(all.stream()
                        .filter(a -> a.tenantId().equals(tenantId) && a.ledgerId().equals(ledgerId))
                        .filter(a -> a.getAccountNumber().equals(number)).findFirst());
            }

            @Override
            public Uni<List<Account>> findAll(TenantRef tenantId, LedgerId ledgerId) {
                return Uni.createFrom().item(all.stream()
                        .filter(a -> a.tenantId().equals(tenantId) && a.ledgerId().equals(ledgerId)).toList());
            }

            @Override
            @Deprecated(forRemoval = true)
            public Uni<Optional<Account>> findById(AccountId id) {
                return Uni.createFrom().item(all.stream().filter(a -> a.id().equals(id)).findFirst());
            }

            @Override
            @Deprecated(forRemoval = true)
            public Uni<Optional<Account>> findByAccountNumber(String number) {
                return Uni.createFrom().item(all.stream().filter(a -> a.getAccountNumber().equals(number)).findFirst());
            }

            @Override
            @Deprecated(forRemoval = true)
            public Uni<List<Account>> findAll() {
                return Uni.createFrom().item(all);
            }

            @Override
            public Uni<Void> save(Account account) {
                return Uni.createFrom().voidItem();
            }
        };

        TrialBalance report = new FinancialReportingService(repository)
                .generateTrialBalance(tenantA, primary, LocalDate.of(2026, 11, 6))
                .await().indefinitely();

        assertTrue(report.balanced());
        assertEquals(Money.of(100L, usd), report.totalDebit());
        assertEquals(Money.of(100L, usd), report.totalCredit());
    }

    @Test
    void reportRejectsMixedAccountCurrenciesInsteadOfAddingIncompatibleAmounts() {
        TenantRef tenant = TenantRef.of("tenant-a");
        LedgerId ledger = LedgerId.primary();
        Account usd = account(tenant, ledger, "1000", AccountType.ASSET, Currency.of("USD"), true);
        Account idr = account(tenant, ledger, "1001", AccountType.ASSET, Currency.of("IDR"), true);

        AccountRepository repository = new ScopedOnlyAccountRepository(List.of(usd, idr));
        assertThrows(IllegalStateException.class, () -> new FinancialReportingService(repository)
                .generateTrialBalance(tenant, ledger, LocalDate.of(2026, 11, 6)).await().indefinitely());
    }

    private static Account account(TenantRef tenant, LedgerId ledger, String number,
                                   AccountType type, Currency currency, boolean debit) {
        Account account = new Account(AccountId.generate(), tenant, ledger, number, number, type, currency);
        if (debit) account.debit(Money.of(100L, currency));
        else account.credit(Money.of(100L, currency));
        return account;
    }

    private static final class ScopedOnlyAccountRepository implements AccountRepository {
        private final List<Account> accounts;
        private ScopedOnlyAccountRepository(List<Account> accounts) { this.accounts = accounts; }
        @Override public Uni<Optional<Account>> findById(TenantRef t, LedgerId l, AccountId id) {
            return Uni.createFrom().item(Optional.empty());
        }
        @Override public Uni<Optional<Account>> findByAccountNumber(TenantRef t, LedgerId l, String n) {
            return Uni.createFrom().item(Optional.empty());
        }
        @Override public Uni<List<Account>> findAll(TenantRef t, LedgerId l) {
            return Uni.createFrom().item(accounts);
        }
        @Override @Deprecated(forRemoval = true) public Uni<Optional<Account>> findById(AccountId id) {
            return Uni.createFrom().item(Optional.empty());
        }
        @Override @Deprecated(forRemoval = true) public Uni<Optional<Account>> findByAccountNumber(String n) {
            return Uni.createFrom().item(Optional.empty());
        }
        @Override @Deprecated(forRemoval = true) public Uni<List<Account>> findAll() {
            return Uni.createFrom().item(accounts);
        }
        @Override public Uni<Void> save(Account account) { return Uni.createFrom().voidItem(); }
    }
}
