package tech.kayys.numou.wallet.adapter.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.aggregate.domain.ConcurrencyConflictException;
import tech.kayys.numou.wallet.domain.LedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PgLedgerRepositoryTest {

    @Test
    void acceptsExactlyOneInsertedLedgerRow() {
        assertDoesNotThrow(() -> PgLedgerRepository.validateInsertResult(1));
    }

    @Test
    void treatsExactReplayAsNoOp() {
        assertDoesNotThrow(() -> PgLedgerRepository.validateInsertResult(0));
    }

    @Test
    void rejectsUnexpectedMultiRowInsertResult() {
        assertThrows(ConcurrencyConflictException.class,
                () -> PgLedgerRepository.validateInsertResult(2));
    }

    @Test
    void parsesPostgresLedgerJsonIncludingRequestFingerprint() throws Exception {
        UUID entryId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        PgLedgerRepository repository = new PgLedgerRepository();
        repository.mapper = new ObjectMapper();

        LedgerEntry entry = repository.parse("""
                {
                  "entry_id": "%s",
                  "wallet_id": "%s",
                  "kind": "CREDIT",
                  "amount": 125.50,
                  "balance_after": 425.50,
                  "ref": "operation-123",
                  "memo": "invoice payment",
                  "at": "2026-10-28T12:00:00Z",
                  "seq": 7,
                  "request_fingerprint": "sha256:abc"
                }
                """.formatted(entryId, walletId));

        assertEquals(entryId, entry.id());
        assertEquals(walletId, entry.walletId());
        assertEquals(LedgerEntry.Kind.CREDIT, entry.kind());
        assertEquals(0, new BigDecimal("125.50").compareTo(entry.amount()));
        assertEquals(0, new BigDecimal("425.50").compareTo(entry.balanceAfter()));
        assertEquals("operation-123", entry.ref());
        assertEquals("invoice payment", entry.memo());
        assertEquals(Instant.parse("2026-10-28T12:00:00Z"), entry.at());
        assertEquals(7L, entry.seq());
        assertEquals("sha256:abc", entry.requestFingerprint());
    }

    @Test
    void parsesLegacyLedgerJsonWithoutRequestFingerprint() {
        PgLedgerRepository repository = new PgLedgerRepository();
        repository.mapper = new ObjectMapper();

        LedgerEntry entry = repository.parse("""
                {
                  "entry_id": "11111111-1111-1111-1111-111111111111",
                  "wallet_id": "22222222-2222-2222-2222-222222222222",
                  "kind": "DEBIT",
                  "amount": -5.00,
                  "balance_after": 10.00,
                  "ref": "legacy-operation",
                  "memo": "",
                  "at": "2026-10-28T12:00:00Z",
                  "seq": 3
                }
                """);

        assertEquals("", entry.requestFingerprint());
    }
}
