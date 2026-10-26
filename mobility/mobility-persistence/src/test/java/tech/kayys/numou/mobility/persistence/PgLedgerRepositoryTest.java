package tech.kayys.numou.mobility.persistence;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.aggregate.domain.ConcurrencyConflictException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PgLedgerRepositoryTest {

    @Test
    void acceptsExactlyOneInsertedLedgerRow() {
        assertThatCode(() -> PgLedgerRepository.requireInserted(1))
                .doesNotThrowAnyException();
    }

    @Test
    void treatsSuppressedLedgerInsertConflictAsRetryableConcurrencyConflict() {
        assertThatThrownBy(() -> PgLedgerRepository.requireInserted(0))
                .isInstanceOf(ConcurrencyConflictException.class)
                .hasMessageContaining("ledger append conflicted");
    }

    @Test
    void rejectsUnexpectedMultiRowInsertResult() {
        assertThatThrownBy(() -> PgLedgerRepository.requireInserted(2))
                .isInstanceOf(ConcurrencyConflictException.class);
    }
}
