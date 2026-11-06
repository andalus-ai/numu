package tech.kayys.numou.accounting.interfaces.repository;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.accounting.domain.identifier.AccountId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.model.Account;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;
import tech.kayys.numou.accounting.domain.valueobject.AccountType;
import tech.kayys.numou.foundation.domain.valueobject.Currency;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryAccountRepositoryScopeTest {

    @Test
    void readsAreIsolatedByTenantAndLedger() {
        InMemoryAccountRepository repository = new InMemoryAccountRepository();
        TenantRef tenantA = TenantRef.of("tenant-a");
        TenantRef tenantB = TenantRef.of("tenant-b");
        LedgerId primary = LedgerId.primary();
        LedgerId tax = LedgerId.of("tax");

        Account tenantAPrimary = account(tenantA, primary, "1000");
        Account tenantATax = account(tenantA, tax, "1000");
        Account tenantBPrimary = account(tenantB, primary, "1000");

        repository.save(tenantAPrimary).await().indefinitely();
        repository.save(tenantATax).await().indefinitely();
        repository.save(tenantBPrimary).await().indefinitely();

        assertEquals(tenantAPrimary,
                repository.findById(tenantA, primary, tenantAPrimary.id()).await().indefinitely().orElseThrow());
        assertTrue(repository.findById(tenantB, primary, tenantAPrimary.id()).await().indefinitely().isEmpty());
        assertTrue(repository.findById(tenantA, tax, tenantAPrimary.id()).await().indefinitely().isEmpty());

        assertEquals(tenantAPrimary,
                repository.findByAccountNumber(tenantA, primary, "1000").await().indefinitely().orElseThrow());
        assertEquals(List.of(tenantAPrimary),
                repository.findAll(tenantA, primary).await().indefinitely());
        assertEquals(List.of(tenantATax),
                repository.findAll(tenantA, tax).await().indefinitely());
        assertEquals(List.of(tenantBPrimary),
                repository.findAll(tenantB, primary).await().indefinitely());
    }

    private static Account account(TenantRef tenantId, LedgerId ledgerId, String number) {
        return new Account(AccountId.generate(), tenantId, ledgerId, number, "Cash",
                AccountType.ASSET, Currency.of("IDR"));
    }
}
