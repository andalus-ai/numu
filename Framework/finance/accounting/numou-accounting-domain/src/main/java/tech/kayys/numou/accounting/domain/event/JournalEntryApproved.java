package tech.kayys.syirkah.accounting.domain.event;

import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;

import java.time.Instant;
import java.util.UUID;

public record JournalEntryApproved(
        UUID eventId,
        Instant occurredAt,
        TenantRef tenantId,
        LedgerId ledgerId,
        JournalEntryId journalEntryId,
        String approvedBy,
        String correlationId,
        String causationId
) implements AccountingEvent {
    public static JournalEntryApproved of(TenantRef tenantId, LedgerId ledgerId, JournalEntryId id, String approvedBy, String corrId, String causId) {
        return new JournalEntryApproved(UUID.randomUUID(), Instant.now(), tenantId, ledgerId, id, approvedBy, corrId, causId);
    }
}
