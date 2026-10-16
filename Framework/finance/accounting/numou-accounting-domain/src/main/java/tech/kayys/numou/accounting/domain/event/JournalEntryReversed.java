package tech.kayys.syirkah.accounting.domain.event;

import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;

import java.time.Instant;
import java.util.UUID;

public record JournalEntryReversed(
        UUID eventId,
        Instant occurredAt,
        TenantRef tenantId,
        LedgerId ledgerId,
        JournalEntryId originalEntryId,
        JournalEntryId reversalEntryId,
        String reversedBy,
        String reason,
        String correlationId,
        String causationId
) implements AccountingEvent {
    public static JournalEntryReversed of(TenantRef tenantId, LedgerId ledgerId, JournalEntryId origId, JournalEntryId revId, String by, String reason, String corrId, String causId) {
        return new JournalEntryReversed(UUID.randomUUID(), Instant.now(), tenantId, ledgerId, origId, revId, by, reason, corrId, causId);
    }
}
