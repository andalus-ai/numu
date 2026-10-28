package tech.kayys.numou.wallet.adapter.postgres;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.aggregate.domain.ConcurrencyConflictException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PgLedgerRepositoryTest {

    @Test
    void acceptsExactlyOneInsertedLedgerRow() {
        assertDoesNotThrow(() -> PgLedgerRepository.requireInserted(1));
    }

    @Test
    void surfacesDuplicateOrSuppressedLedgerInsertAsConflict() {
        assertThrows(ConcurrencyConflictException.class,
                () -> PgLedgerRepository.requireInserted(0));
    }

    @Test
    void rejectsUnexpectedMultiRowInsertResult() {
        assertThrows(ConcurrencyConflictException.class,
                () -> PgLedgerRepository.requireInserted(2));
    }
}
