package tech.kayys.numou.accounting.interfaces.repository;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.accounting.domain.identifier.JournalEntryId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.model.JournalEntry;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryJournalEntryRepositoryScopeTest {

    @Test
    void journalReadsAreIsolatedByTenantAndLedger() {
        InMemoryJournalEntryRepository repository = new InMemoryJournalEntryRepository();
        TenantRef tenantA = TenantRef.of("tenant-a");
        TenantRef tenantB = TenantRef.of("tenant-b");
        LedgerId primary = LedgerId.primary();
        LedgerId tax = LedgerId.of("tax");

        JournalEntry tenantAPrimary = entry(tenantA, primary, "JE-A");
        JournalEntry tenantATax = entry(tenantA, tax, "JE-T");
        JournalEntry tenantBPrimary = entry(tenantB, primary, "JE-B");

        repository.save(tenantAPrimary).await().indefinitely();
        repository.save(tenantATax).await().indefinitely();
        repository.save(tenantBPrimary).await().indefinitely();

        assertEquals(tenantAPrimary, repository.findById(tenantA, primary, tenantAPrimary.id())
                .await().indefinitely().orElseThrow());
        assertTrue(repository.findById(tenantB, primary, tenantAPrimary.id())
                .await().indefinitely().isEmpty());
        assertTrue(repository.findById(tenantA, tax, tenantAPrimary.id())
                .await().indefinitely().isEmpty());
        assertEquals(List.of(tenantAPrimary), repository.findAll(tenantA, primary).await().indefinitely());
        assertEquals(List.of(tenantATax), repository.findAll(tenantA, tax).await().indefinitely());
        assertEquals(List.of(tenantBPrimary), repository.findAll(tenantB, primary).await().indefinitely());
    }

    private static JournalEntry entry(TenantRef tenantId, LedgerId ledgerId, String number) {
        return new JournalEntry(JournalEntryId.generate(), tenantId, ledgerId, number, Instant.now(), number);
    }
}
