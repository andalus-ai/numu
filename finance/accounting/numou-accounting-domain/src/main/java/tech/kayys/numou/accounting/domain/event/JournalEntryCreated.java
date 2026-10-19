package tech.kayys.numou.accounting.domain.event;

import tech.kayys.numou.accounting.domain.identifier.JournalEntryId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;

import java.time.Instant;
import java.util.UUID;

public record JournalEntryCreated(
        UUID eventId,
        Instant occurredAt,
        TenantRef tenantId,
        LedgerId ledgerId,
        JournalEntryId journalEntryId,
        String entryNumber,
        String createdBy,
        String correlationId,
        String causationId
) implements AccountingEvent {
    public static JournalEntryCreated of(TenantRef tenantId, LedgerId ledgerId, JournalEntryId id, String entryNumber, String createdBy, String corrId, String causId) {
        return new JournalEntryCreated(UUID.randomUUID(), Instant.now(), tenantId, ledgerId, id, entryNumber, createdBy, corrId, causId);
    }
}
