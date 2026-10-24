package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeadLetterReplayServiceTest {

    private final InboundEventKey key = new InboundEventKey(
            TenantId.of("tenant-a"), "provider-x", "account-1", "evt-42");

    @Test
    void deniesReplayBeforePersistenceWhenActorIsNotAuthorized() {
        AtomicInteger calls = new AtomicInteger();
        DeadLetterOperations operations = new StubOperations(calls);
        DeadLetterReplayService service = new DeadLetterReplayService(
                operations, (actor, eventKey) -> false,
                Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.replay(key, "operator-1", "manual review"))
                .isInstanceOf(SecurityException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void authorizedReplayPassesStableEventIdentityToStore() {
        AtomicInteger calls = new AtomicInteger();
        DeadLetterOperations operations = new StubOperations(calls);
        DeadLetterReplayService service = new DeadLetterReplayService(
                operations, (actor, eventKey) -> actor.equals("operator-1") && eventKey.equals(key),
                Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC));

        assertThat(service.replay(key, "operator-1", "reviewed and approved"))
                .isEqualTo(DeadLetterReplayResult.REQUEUED);
        assertThat(calls).hasValue(1);
    }

    @Test
    void rejectsBlankActorAndReason() {
        DeadLetterReplayService service = new DeadLetterReplayService(
                new StubOperations(new AtomicInteger()), (actor, eventKey) -> true, Clock.systemUTC());

        assertThatThrownBy(() -> service.replay(key, " ", "reason"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.replay(key, "operator-1", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class StubOperations implements DeadLetterOperations {
        private final AtomicInteger calls;

        private StubOperations(AtomicInteger calls) {
            this.calls = calls;
        }

        @Override
        public List<DeadLetterEvent> find(DeadLetterQuery query) {
            return List.of();
        }

        @Override
        public DeadLetterReplayResult replay(
                InboundEventKey key, String actorId, String reason, Instant requestedAt) {
            calls.incrementAndGet();
            return DeadLetterReplayResult.REQUEUED;
        }
    }
}
