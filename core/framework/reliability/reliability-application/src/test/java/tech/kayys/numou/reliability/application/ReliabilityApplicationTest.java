package tech.kayys.numou.reliability.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.identifier.CorrelationId;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.event.domain.valueobject.EventVersion;
import tech.kayys.numou.event.spi.port.EventPublisherPort;
import tech.kayys.numou.reliability.application.idempotency.DuplicateRequestException;
import tech.kayys.numou.reliability.application.idempotency.IdempotentExecutor;
import tech.kayys.numou.reliability.application.inbox.InboxProcessor;
import tech.kayys.numou.reliability.application.outbox.OutboxRelayService;
import tech.kayys.numou.reliability.idempotency.IdempotencyOutcome;
import tech.kayys.numou.reliability.inbox.InboxArrival;
import tech.kayys.numou.reliability.inbox.InboxEntry;
import tech.kayys.numou.reliability.outbox.OutboxEntry;
import tech.kayys.numou.reliability.retry.RetryPolicy;
import tech.kayys.numou.reliability.spi.port.IdempotencyStorePort;
import tech.kayys.numou.reliability.spi.port.InboxStorePort;
import tech.kayys.numou.reliability.spi.port.OutboxStorePort;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReliabilityApplicationTest {

    record Sample(UUID eventId, Instant occurredAt) implements BusinessEvent {
        public String eventType() { return "test.sample.happened"; }
        public EventVersion eventVersion() { return EventVersion.V1; }
    }

    private static OutboxEntry entry() {
        final var meta = EventMetadata.of(UUID.randomUUID(), null, "tester", CorrelationId.generate());
        return OutboxEntry.pending(EventEnvelope.of(meta, new Sample(UUID.randomUUID(), Instant.now())));
    }

    static final class MemOutbox implements OutboxStorePort {
        final Map<UUID, OutboxEntry> rows = new LinkedHashMap<>();
        Instant now = Instant.now().plusSeconds(1);
        public Uni<Void> append(List<OutboxEntry> es) { es.forEach(e -> rows.put(e.entryId(), e)); return Uni.createFrom().voidItem(); }
        public Uni<List<OutboxEntry>> nextDue(int limit) {
            return Uni.createFrom().item(rows.values().stream()
                    .filter(e -> e.isDue(now) && !e.exhausted()).limit(limit).toList());
        }
        public Uni<Void> update(OutboxEntry e) { rows.put(e.entryId(), e); return Uni.createFrom().voidItem(); }
        public Uni<OutboxEntry> findById(UUID id) { return Uni.createFrom().item(rows.get(id)); }
        public Uni<Long> countPending() { return Uni.createFrom().item(rows.values().stream().filter(e -> !e.published()).count()); }
    }

    static class ScriptedPublisher implements EventPublisherPort {
        final Deque<Throwable> failures = new ArrayDeque<>();
        final List<EventEnvelope<? extends BusinessEvent>> sent = new ArrayList<>();
        public Uni<Void> publish(EventEnvelope<? extends BusinessEvent> e) {
            final var f = failures.poll();
            if (f != null) return Uni.createFrom().failure(f);
            sent.add(e); return Uni.createFrom().voidItem();
        }
        public Uni<Void> publishAll(List<EventEnvelope<? extends BusinessEvent>> es) { es.forEach(x -> publish(x)); return Uni.createFrom().voidItem(); }
    }

    private OutboxRelayService relay(MemOutbox store, ScriptedPublisher pub) {
        return new OutboxRelayService(store, pub, RetryPolicy.DEFAULT, 50, () -> store.now, () -> 0.5,
                OutboxRelayService.defaultClassifier());
    }

    // ---- outbox ----

    @Test
    void relaysDueEntriesAndMarksThemPublished() {
        final var store = new MemOutbox(); final var pub = new ScriptedPublisher();
        final var a = entry(); final var b = entry();
        store.append(List.of(a, b)).await().indefinitely();
        assertThat(relay(store, pub).runOnce().await().indefinitely()).isEqualTo(2);
        assertThat(pub.sent).hasSize(2);
        assertThat(store.rows.values()).allMatch(OutboxEntry::published);
        assertThat(relay(store, pub).runOnce().await().indefinitely()).isZero(); // nothing left
    }

    @Test
    void transientFailureBacksOffThenSucceeds() {
        final var store = new MemOutbox(); final var pub = new ScriptedPublisher();
        final var e = entry(); store.append(List.of(e)).await().indefinitely();
        pub.failures.add(new IllegalStateException("broker down"));

        assertThat(relay(store, pub).runOnce().await().indefinitely()).isZero();
        final var afterFail = store.rows.get(e.entryId());
        assertThat(afterFail.attempts()).isEqualTo(1);
        assertThat(afterFail.availableAt()).isAfter(store.now);       // backed off
        assertThat(relay(store, pub).runOnce().await().indefinitely()).isZero(); // not due yet

        store.now = afterFail.availableAt().plusSeconds(1);
        assertThat(relay(store, pub).runOnce().await().indefinitely()).isEqualTo(1);
        assertThat(store.rows.get(e.entryId()).published()).isTrue();
    }

    @Test
    void oneFailingEntryDoesNotBlockTheRestOfTheBatch() {
        final var store = new MemOutbox(); final var pub = new ScriptedPublisher();
        final var bad = entry(); final var good = entry();
        store.append(List.of(bad, good)).await().indefinitely();
        pub.failures.add(new IllegalStateException("first send fails"));
        assertThat(relay(store, pub).runOnce().await().indefinitely()).isEqualTo(1);
        assertThat(store.rows.get(good.entryId()).published()).isTrue();
        assertThat(store.rows.get(bad.entryId()).published()).isFalse();
    }

    @Test
    void permanentFailureIsDeadLetteredImmediately() {
        final var store = new MemOutbox(); final var pub = new ScriptedPublisher();
        final var e = entry(); store.append(List.of(e)).await().indefinitely();
        pub.failures.add(new IllegalArgumentException("cannot serialise"));
        relay(store, pub).runOnce().await().indefinitely();
        final var row = store.rows.get(e.entryId());
        assertThat(row.exhausted()).isTrue();
        assertThat(row.published()).isFalse();
        store.now = store.now.plus(Duration.ofDays(1));
        assertThat(relay(store, pub).runOnce().await().indefinitely()).isZero(); // never retried
    }

    @Test
    void entryIsMarkedPublishedOnlyAfterTransportAck() {
        final var store = new MemOutbox();
        final var neverAcks = new ScriptedPublisher() {
            @Override public Uni<Void> publish(EventEnvelope<? extends BusinessEvent> e) {
                assertThat(store.rows.values()).noneMatch(OutboxEntry::published); // still unpublished while sending
                return super.publish(e);
            }
        };
        store.append(List.of(entry())).await().indefinitely();
        relay(store, neverAcks).runOnce().await().indefinitely();
    }

    // ---- idempotency ----

    static final class MemIdempotency implements IdempotencyStorePort {
        final Map<String, String> results = new ConcurrentHashMap<>();
        public Uni<IdempotencyOutcome> claim(String key, String scope) {
            final var k = scope + "|" + key;
            final var prev = results.putIfAbsent(k, "");
            return Uni.createFrom().item(prev == null
                    ? IdempotencyOutcome.proceed(Instant.now())
                    : IdempotencyOutcome.duplicate(Instant.now(), prev));
        }
        public Uni<Void> recordResult(String key, String scope, String ref) { results.put(scope + "|" + key, ref); return Uni.createFrom().voidItem(); }
        public Uni<Void> release(String key, String scope) { results.remove(scope + "|" + key, ""); return Uni.createFrom().voidItem(); }
    }

    @Test
    void secondCallWithSameKeyReplaysResultWithoutRunningWork() {
        final var exec = new IdempotentExecutor(new MemIdempotency());
        final var runs = new AtomicInteger();
        final var first = exec.execute("k1", "reservation.create", () -> { runs.incrementAndGet(); return Uni.createFrom().item("res-42"); },
                r -> r, r -> r).await().indefinitely();
        final var second = exec.execute("k1", "reservation.create", () -> { runs.incrementAndGet(); return Uni.createFrom().item("res-99"); },
                r -> r, r -> r).await().indefinitely();
        assertThat(first).isEqualTo("res-42");
        assertThat(second).isEqualTo("res-42");
        assertThat(runs).hasValue(1);
    }

    @Test
    void failedWorkReleasesTheClaimSoRetryCanRun() {
        final var exec = new IdempotentExecutor(new MemIdempotency());
        assertThatThrownBy(() -> exec.execute("k2", "s", () -> Uni.<String>createFrom().failure(new IllegalStateException("db")), r -> r, r -> r)
                .await().indefinitely()).isInstanceOf(IllegalStateException.class);
        assertThat(exec.execute("k2", "s", () -> Uni.createFrom().item("ok"), r -> r, r -> r).await().indefinitely()).isEqualTo("ok");
    }

    @Test
    void resultPersistenceFailureRetainsClaimAndPreventsBusinessWorkFromRunningAgain() {
        final var delegate = new MemIdempotency();
        final IdempotencyStorePort failingResultStore = new IdempotencyStorePort() {
            @Override
            public Uni<IdempotencyOutcome> claim(String key, String scope) {
                return delegate.claim(key, scope);
            }

            @Override
            public Uni<Void> recordResult(String key, String scope, String ref) {
                return Uni.createFrom().failure(new IllegalStateException("idempotency store unavailable"));
            }

            @Override
            public Uni<Void> release(String key, String scope) {
                return delegate.release(key, scope);
            }
        };
        final var exec = new IdempotentExecutor(failingResultStore);
        final var runs = new AtomicInteger();

        assertThatThrownBy(() -> exec.execute("k-result-failure", "reservation.create",
                () -> {
                    runs.incrementAndGet();
                    return Uni.createFrom().item("reservation-42");
                }, r -> r, r -> r).await().indefinitely())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("idempotency store unavailable");

        // Business work succeeded, so the claim must not be released when result persistence fails.
        assertThat(delegate.results).containsKey("reservation.create|k-result-failure");
        assertThatThrownBy(() -> exec.execute("k-result-failure", "reservation.create",
                () -> {
                    runs.incrementAndGet();
                    return Uni.createFrom().item("reservation-99");
                }, r -> r, r -> r).await().indefinitely())
                .isInstanceOf(DuplicateRequestException.class);
        assertThat(runs).hasValue(1);
    }

    @Test
    void duplicateWhileFirstIsStillRunningIsRejectedNotReExecuted() {
        final var store = new MemIdempotency();
        store.claim("k3", "s").await().indefinitely(); // first attempt claimed, no result yet
        final var exec = new IdempotentExecutor(store);
        assertThatThrownBy(() -> exec.execute("k3", "s", () -> Uni.createFrom().item("x"), r -> r, r -> r).await().indefinitely())
                .isInstanceOf(DuplicateRequestException.class);
    }

    @Test
    void scopesAreIndependent() {
        final var exec = new IdempotentExecutor(new MemIdempotency());
        assertThat(exec.execute("same", "a", () -> Uni.createFrom().item("A"), r -> r, r -> r).await().indefinitely()).isEqualTo("A");
        assertThat(exec.execute("same", "b", () -> Uni.createFrom().item("B"), r -> r, r -> r).await().indefinitely()).isEqualTo("B");
    }

    @Test
    void blankKeyIsRejected() {
        final var exec = new IdempotentExecutor(new MemIdempotency());
        assertThatThrownBy(() -> exec.execute(" ", "s", () -> Uni.createFrom().item("x"), r -> r, r -> r).await().indefinitely())
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- inbox ----

    static final class MemInbox implements InboxStorePort {
        final Map<String, InboxEntry> rows = new ConcurrentHashMap<>();
        final Map<String, UUID> claims = new ConcurrentHashMap<>();
        final Map<String, Instant> leases = new ConcurrentHashMap<>();
        public Uni<InboxArrival> recordArrival(String id, String type) {
            final var fresh = InboxEntry.firstArrival(id, type);
            final var existing = rows.putIfAbsent(id, fresh);
            return Uni.createFrom().item(new InboxArrival(existing == null ? fresh : existing, existing == null));
        }
        public Uni<Boolean> alreadyHandled(String id) {
            return Uni.createFrom().item(rows.get(id) != null && rows.get(id).handled());
        }
        public Uni<Boolean> claim(String id, UUID token, Instant now, Instant until) {
            final var entry = rows.get(id);
            if (entry == null || entry.handled()) return Uni.createFrom().item(false);
            final var acquired = new java.util.concurrent.atomic.AtomicBoolean();
            claims.compute(id, (key, current) -> {
                final var expiry = leases.get(key);
                if (current == null || (expiry != null && !expiry.isAfter(now))) {
                    leases.put(key, until);
                    acquired.set(true);
                    return token;
                }
                return current;
            });
            return Uni.createFrom().item(acquired.get());
        }
        public Uni<Void> releaseClaim(String id, UUID token) {
            if (claims.remove(id, token)) leases.remove(id);
            return Uni.createFrom().voidItem();
        }
        public Uni<Void> markHandled(String id, UUID token) {
            if (claims.remove(id, token)) {
                leases.remove(id);
                rows.computeIfPresent(id, (key, entry) -> entry.markHandled());
            }
            return Uni.createFrom().voidItem();
        }
        public Uni<InboxEntry> findById(UUID id) {
            return Uni.createFrom().item(rows.values().stream().filter(x -> x.entryId().equals(id)).findFirst().orElse(null));
        }
    }

    @Test
    void duplicateEventIsHandledOnce() {
        final var p = new InboxProcessor(new MemInbox()); final var runs = new AtomicInteger();
        assertThat(p.process("EVT-1", "t", () -> { runs.incrementAndGet(); return Uni.createFrom().voidItem(); }).await().indefinitely()).isTrue();
        assertThat(p.process("EVT-1", "t", () -> { runs.incrementAndGet(); return Uni.createFrom().voidItem(); }).await().indefinitely()).isFalse();
        assertThat(runs).hasValue(1);
    }

    @Test
    void eventWhoseHandlerFailedCanBeClaimedAgain() {
        final var p = new InboxProcessor(new MemInbox()); final var runs = new AtomicInteger();
        assertThatThrownBy(() -> p.process("EVT-2", "t", () -> { runs.incrementAndGet(); return Uni.createFrom().failure(new IllegalStateException("x")); })
                .await().indefinitely()).isInstanceOf(IllegalStateException.class);
        assertThat(p.process("EVT-2", "t", () -> { runs.incrementAndGet(); return Uni.createFrom().voidItem(); }).await().indefinitely()).isTrue();
        assertThat(runs).hasValue(2);
    }

    @Test
    void concurrentDeliveryAcquiresOnlyOneInboxClaim() throws Exception {
        final var processor = new InboxProcessor(new MemInbox());
        final var handlerStarted = new CountDownLatch(1);
        final var releaseHandler = new CountDownLatch(1);
        final var runs = new AtomicInteger();
        final var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            final var first = pool.submit(() -> processor.process("EVT-CONCURRENT", "t", () -> {
                runs.incrementAndGet();
                handlerStarted.countDown();
                return Uni.createFrom().voidItem().invoke(() -> {
                    try {
                        if (!releaseHandler.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                });
            }).await().indefinitely());

            assertThat(handlerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            final var second = processor.process("EVT-CONCURRENT", "t", () -> {
                runs.incrementAndGet();
                return Uni.createFrom().voidItem();
            }).await().indefinitely();
            releaseHandler.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(second).isFalse();
            assertThat(runs).hasValue(1);
        } finally {
            releaseHandler.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void completionPersistenceFailureRetainsClaimForLeaseWindow() {
        final var delegate = new MemInbox();
        final InboxStorePort failingStore = new InboxStorePort() {
            public Uni<InboxArrival> recordArrival(String id, String type) { return delegate.recordArrival(id, type); }
            public Uni<Boolean> alreadyHandled(String id) { return delegate.alreadyHandled(id); }
            public Uni<Boolean> claim(String id, UUID token, Instant now, Instant until) { return delegate.claim(id, token, now, until); }
            public Uni<Void> releaseClaim(String id, UUID token) { return delegate.releaseClaim(id, token); }
            public Uni<Void> markHandled(String id, UUID token) { return Uni.createFrom().failure(new IllegalStateException("store unavailable")); }
            public Uni<InboxEntry> findById(UUID id) { return delegate.findById(id); }
        };
        final var processor = new InboxProcessor(failingStore);
        final var runs = new AtomicInteger();
        assertThatThrownBy(() -> processor.process("EVT-MARK-FAIL", "t", () -> {
            runs.incrementAndGet();
            return Uni.createFrom().voidItem();
        }).await().indefinitely()).isInstanceOf(IllegalStateException.class);
        assertThat(processor.process("EVT-MARK-FAIL", "t", () -> {
            runs.incrementAndGet();
            return Uni.createFrom().voidItem();
        }).await().indefinitely()).isFalse();
        assertThat(runs).hasValue(1);
    }
}
}
