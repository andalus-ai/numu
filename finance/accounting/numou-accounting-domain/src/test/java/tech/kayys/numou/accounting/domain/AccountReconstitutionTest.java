package tech.kayys.numou.accounting.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.accounting.domain.identifier.AccountId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.model.Account;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;
import tech.kayys.numou.accounting.domain.valueobject.AccountStatus;
import tech.kayys.numou.accounting.domain.valueobject.AccountType;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AccountReconstitutionTest {

    @Test
    void restoresCompleteAccountStateAndOwnership() {
        AccountId id = AccountId.of(UUID.randomUUID());
        AccountId parentId = AccountId.of(UUID.randomUUID());
        TenantRef tenant = TenantRef.of("tenant-42");
        LedgerId ledger = LedgerId.of("management");
        Money balance = Money.of(new BigDecimal("1250.1250"), "IDR");

        Account restored = Account.reconstitute(
                id, tenant, ledger, "1100", "Cash", "Operating cash",
                AccountType.ASSET, AccountStatus.FROZEN, balance, parentId,
                false, false
        );

        assertEquals(id, restored.id());
        assertEquals(tenant, restored.tenantId());
        assertEquals(ledger, restored.ledgerId());
        assertEquals("1100", restored.getAccountNumber());
        assertEquals("Cash", restored.getName());
        assertEquals("Operating cash", restored.getDescription());
        assertEquals(AccountType.ASSET, restored.getAccountType());
        assertEquals(AccountStatus.FROZEN, restored.getStatus());
        assertEquals(0, new BigDecimal("1250.1250").compareTo(restored.getCurrentBalance().amount()));
        assertEquals("IDR", restored.getCurrentBalance().currency().code());
        assertEquals(parentId, restored.getParentAccountId());
        assertFalse(restored.isActive());
        assertFalse(restored.isShariaCompliant());
    }

    @Test
    void rejectsMissingBalanceOrStatus() {
        assertThrows(NullPointerException.class, () -> Account.reconstitute(
                AccountId.generate(), TenantRef.of("t"), LedgerId.primary(),
                "1000", "Cash", null, AccountType.ASSET, AccountStatus.ACTIVE,
                null, null, true, true
        ));
        assertThrows(NullPointerException.class, () -> Account.reconstitute(
                AccountId.generate(), TenantRef.of("t"), LedgerId.primary(),
                "1000", "Cash", null, AccountType.ASSET, null,
                Money.zero("IDR"), null, true, true
        ));
    }
}
