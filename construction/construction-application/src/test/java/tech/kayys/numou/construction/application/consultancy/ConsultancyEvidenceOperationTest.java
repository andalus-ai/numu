package tech.kayys.numou.construction.application.consultancy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsultancyEvidenceOperationTest {
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");

    private ConsultancyEvidenceOperation pending() {
        return ConsultancyEvidenceOperation.pending(
                UUID.randomUUID(), " tenant-a ", "request-123",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "supporting-document", UUID.randomUUID(), UUID.randomUUID(), NOW);
    }

    @Test
    void createsNormalizedVersionPinnedPendingOperation() {
        var operation = pending();

        assertThat(operation.tenantId()).isEqualTo("tenant-a");
        assertThat(operation.status()).isEqualTo(ConsultancyEvidenceOperation.Status.PENDING);
        assertThat(operation.attempts()).isZero();
        assertThat(operation.documentVersionId()).isNotNull();
    }

    @Test
    void allowsRetryAfterRetryableFailureAndThenCompletion() {
        var failed = pending().retryableFailure("Document service unavailable", NOW.plusSeconds(1));
        var retried = failed.retry(NOW.plusSeconds(2));
        var linked = retried.linked(NOW.plusSeconds(3));

        assertThat(failed.status()).isEqualTo(ConsultancyEvidenceOperation.Status.RETRYABLE_FAILURE);
        assertThat(retried.status()).isEqualTo(ConsultancyEvidenceOperation.Status.PENDING);
        assertThat(linked.status()).isEqualTo(ConsultancyEvidenceOperation.Status.LINKED);
        assertThat(linked.attempts()).isEqualTo(2);
        assertThat(linked.lastFailure()).isNull();
    }

    @Test
    void rejectsRetryOfTerminalFailureAndDuplicateCompletionTransition() {
        var terminal = pending().terminalFailure("Document version rejected", NOW.plusSeconds(1));

        assertThatThrownBy(() -> terminal.retry(NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> terminal.linked(NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsBlankIdempotencyKeyAndBackwardsTime() {
        assertThatThrownBy(() -> ConsultancyEvidenceOperation.pending(
                UUID.randomUUID(), "tenant-a", " ", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "evidence", UUID.randomUUID(), UUID.randomUUID(), NOW))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> pending().retryableFailure("failed", NOW.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
