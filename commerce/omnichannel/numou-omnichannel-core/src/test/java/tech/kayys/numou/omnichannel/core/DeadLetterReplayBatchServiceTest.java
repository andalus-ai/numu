package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeadLetterReplayBatchServiceTest {
    private final InboundEventKey allowedKey = key("event-1");
    private final InboundEventKey deniedKey = key("event-2");

    @Test
    void authorizesEachItemAndReturnsPartialOutcomes() {
        AtomicInteger writes = new AtomicInteger();
        DeadLetterOperations operations = new StubOperations(writes);
        DeadLetterReplayBatchService service = new DeadLetterReplayBatchService(
                operations, (actor, key) -> key.equals(allowedKey),
                Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC));

        List<DeadLetterReplayBatchItem> results = service.replay(
                List.of(allowedKey, deniedKey), "operator", "reviewed");

        assertThat(results).extracting(DeadLetterReplayBatchItem::outcome).containsExactly(
                DeadLetterReplayBatchItem.Outcome.REQUEUED,
                DeadLetterReplayBatchItem.Outcome.FORBIDDEN);
        assertThat(writes).hasValue(1);
    }

    @Test
    void rejectsEmptyOversizedAndDuplicateBatchesBeforeWriting() {
        AtomicInteger writes = new AtomicInteger();
        DeadLetterReplayBatchService service = new DeadLetterReplayBatchService(
                new StubOperations(writes), (actor, key) -> true, Clock.systemUTC());

        assertThatThrownBy(() -> service.replay(List.of(), "operator", "reason"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.replay(
                java.util.Collections.nCopies(DeadLetterReplayBatchService.MAX_BATCH_SIZE + 1,
                        allowedKey), "operator", "reason"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.replay(
                List.of(allowedKey, allowedKey), "operator", "reason"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(writes).hasValue(0);
    }

    private static InboundEventKey key(String eventId) {
        return new InboundEventKey(TenantId.of("tenant-a"), "provider", "account", eventId);
    }

    private static final class StubOperations implements DeadLetterOperations {
        private final AtomicInteger writes;
        private StubOperations(AtomicInteger writes) { this.writes = writes; }

        @Override public List<DeadLetterEvent> find(DeadLetterQuery query) { return List.of(); }

        @Override public DeadLetterReplayResult replay(
                InboundEventKey key, String actorId, String reason, Instant requestedAt) {
            writes.incrementAndGet();
            return DeadLetterReplayResult.REQUEUED;
        }
    }
}
