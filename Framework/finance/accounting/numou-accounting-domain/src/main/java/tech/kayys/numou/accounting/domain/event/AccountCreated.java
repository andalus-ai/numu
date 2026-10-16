package tech.kayys.syirkah.accounting.domain.event;

import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.accounting.domain.valueobject.AccountType;

import java.time.Instant;
import java.util.UUID;

public record AccountCreated(
        UUID eventId,
        Instant occurredAt,
        TenantRef tenantId,
        LedgerId ledgerId,
        AccountId accountId,
        String accountNumber,
        String accountName,
        AccountType accountType,
        String correlationId,
        String causationId
) implements AccountingEvent {
    public static AccountCreated of(TenantRef tenantId, LedgerId ledgerId, AccountId id, String num, String name, AccountType type, String corrId, String causId) {
        return new AccountCreated(UUID.randomUUID(), Instant.now(), tenantId, ledgerId, id, num, name, type, corrId, causId);
    }
}
