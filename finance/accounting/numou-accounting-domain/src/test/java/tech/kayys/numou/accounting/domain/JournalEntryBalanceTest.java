package tech.kayys.numou.accounting.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.accounting.domain.identifier.AccountId;
import tech.kayys.numou.accounting.domain.identifier.JournalEntryId;
import tech.kayys.numou.accounting.domain.model.JournalEntry;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("JournalEntry balance invariant")
class JournalEntryBalanceTest {

    private static Money idr(String v) {
        return Money.of(new BigDecimal(v), "IDR");
    }

    private static JournalEntry entry() {
        return new JournalEntry(JournalEntryId.generate(), "JE-1", Instant.parse("2026-01-01T00:00:00Z"), "t");
    }

    @Test
    @DisplayName("balances when debit and credit differ only in scale (100 vs 100.00)")
    void scaleInsensitive() {
        JournalEntry e = entry();
        e.addLine(AccountId.generate(), idr("100.00"), idr("0"), "dr");
        e.addLine(AccountId.generate(), idr("0"), idr("100"), "cr");
        e.post("tester");
        assertEquals(JournalEntry.EntryStatus.POSTED, e.getStatus());
    }

    @Test
    @DisplayName("still rejects genuinely unbalanced entries")
    void rejectsUnbalanced() {
        JournalEntry e = entry();
        e.addLine(AccountId.generate(), idr("100"), idr("0"), "dr");
        e.addLine(AccountId.generate(), idr("0"), idr("99.99"), "cr");
        assertThrows(IllegalStateException.class, () -> e.post("tester"));
    }
}
