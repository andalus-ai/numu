package tech.kayys.numou.construction.application.idempotency;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.construction.spi.idempotency.CommandIdempotencyStore;
import tech.kayys.numou.construction.spi.idempotency.TransactionalCommandContext;
import tech.kayys.numou.construction.spi.idempotency.TransactionalCommandRunner;
import tech.kayys.numou.construction.spi.outbox.TransactionalOutboxEvent;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class IdempotentCommandExecutorTest {
    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");
    private static final String TENANT = "tenant-a";
    private static final String KEY = "request-123";
    private static final String OPERATION = "boq.capture";
    private static final String FINGERPRINT = "sha256:abc";

    @Test
    void reservedRequestRunsActionAndStoresEncodedOutcome() {
        FakeContext context = new FakeContext(reserved());
        RecordingRunner runner = new RecordingRunner(context);
        IdempotentCommandExecutor executor = new IdempotentCommandExecutor(runner, new FakeCodec());
        AtomicInteger actions = new AtomicInteger();

        String result = executor.execute(TENANT, KEY, OPERATION, FINGERPRINT, NOW, String.class,
                tx -> {
                    assertSame(context, tx);
                    actions.incrementAndGet();
                    return Uni.createFrom().item("created");
                }).await().indefinitely();

        assertEquals("created", result);
        assertEquals(1, runner.calls);
        assertEquals(1, actions.get());
        assertEquals(1, context.reserveCalls);
        assertEquals(1, context.completeCalls);
        assertEquals(TENANT, context.completedTenant);
        assertEquals(KEY, context.completedKey);
        assertEquals(FINGERPRINT, context.completedFingerprint);
        assertEquals("json:created", context.completedResponse);
        assertEquals(NOW, context.completedAt);
    }

    @Test
    void completedMatchingRequestReturnsStoredResponseWithoutRunningAction() {
        var outcome = new CommandIdempotencyStore.StoredOutcome(
                OPERATION, FINGERPRINT, "COMPLETED", "json:previous-result");
        FakeContext context = new FakeContext(new CommandIdempotencyStore.ReservationResult(
                CommandIdempotencyStore.ReservationStatus.EXISTING_MATCH, Optional.of(outcome)));
        IdempotentCommandExecutor executor = new IdempotentCommandExecutor(
                new RecordingRunner(context), new FakeCodec());
        AtomicInteger actions = new AtomicInteger();

        String result = executor.execute(TENANT, KEY, OPERATION, FINGERPRINT, NOW, String.class,
                tx -> { actions.incrementAndGet(); return Uni.createFrom().item("unexpected"); })
                .await().indefinitely();

        assertEquals("decoded:json:previous-result", result);
        assertEquals(0, actions.get());
        assertEquals(0, context.completeCalls);
    }

    @Test
    void reusedKeyWithDifferentRequestFailsBeforeAction() {
        FakeContext context = new FakeContext(new CommandIdempotencyStore.ReservationResult(
                CommandIdempotencyStore.ReservationStatus.KEY_REUSED_WITH_DIFFERENT_REQUEST,
                Optional.empty()));
        IdempotentCommandExecutor executor = new IdempotentCommandExecutor(
                new RecordingRunner(context), new FakeCodec());
        AtomicInteger actions = new AtomicInteger();

        assertThrows(IdempotentCommandExecutor.IdempotencyConflictException.class,
                () -> executor.execute(TENANT, KEY, OPERATION, FINGERPRINT, NOW, String.class,
                        tx -> { actions.incrementAndGet(); return Uni.createFrom().item("x"); })
                        .await().indefinitely());
        assertEquals(0, actions.get());
        assertEquals(0, context.completeCalls);
    }

    @Test
    void matchingButIncompleteReservationFailsClosed() {
        var outcome = new CommandIdempotencyStore.StoredOutcome(
                OPERATION, FINGERPRINT, "IN_PROGRESS", null);
        FakeContext context = new FakeContext(new CommandIdempotencyStore.ReservationResult(
                CommandIdempotencyStore.ReservationStatus.EXISTING_MATCH, Optional.of(outcome)));
        IdempotentCommandExecutor executor = new IdempotentCommandExecutor(
                new RecordingRunner(context), new FakeCodec());

        assertThrows(IdempotentCommandExecutor.IdempotencyInProgressException.class,
                () -> executor.execute(TENANT, KEY, OPERATION, FINGERPRINT, NOW, String.class,
                        tx -> Uni.createFrom().item("must-not-run")).await().indefinitely());
        assertEquals(0, context.completeCalls);
    }

    @Test
    void actionFailureIsPropagatedAndOutcomeIsNotCompleted() {
        FakeContext context = new FakeContext(reserved());
        IdempotentCommandExecutor executor = new IdempotentCommandExecutor(
                new RecordingRunner(context), new FakeCodec());
        IllegalStateException failure = new IllegalStateException("business write failed");

        IllegalStateException actual = assertThrows(IllegalStateException.class,
                () -> executor.execute(TENANT, KEY, OPERATION, FINGERPRINT, NOW, String.class,
                        tx -> Uni.createFrom().failure(failure)).await().indefinitely());

        assertSame(failure, actual);
        assertEquals(0, context.completeCalls);
    }

    @Test
    void failedCompletionCausesCommandFailure() {
        FakeContext context = new FakeContext(reserved());
        context.completeResult = false;
        IdempotentCommandExecutor executor = new IdempotentCommandExecutor(
                new RecordingRunner(context), new FakeCodec());

        assertThrows(IdempotentCommandExecutor.IdempotencyConflictException.class,
                () -> executor.execute(TENANT, KEY, OPERATION, FINGERPRINT, NOW, String.class,
                        tx -> Uni.createFrom().item("created")).await().indefinitely());
        assertEquals(1, context.completeCalls);
    }

    @Test
    void invalidTrustedInputsAreRejectedBeforeOpeningTransaction() {
        RecordingRunner runner = new RecordingRunner(new FakeContext(reserved()));
        IdempotentCommandExecutor executor = new IdempotentCommandExecutor(runner, new FakeCodec());

        assertThrows(IllegalArgumentException.class, () -> executor.execute(" ", KEY, OPERATION,
                FINGERPRINT, NOW, String.class, tx -> Uni.createFrom().item("x")));
        assertEquals(0, runner.calls);
    }

    private static CommandIdempotencyStore.ReservationResult reserved() {
        return new CommandIdempotencyStore.ReservationResult(
                CommandIdempotencyStore.ReservationStatus.RESERVED, Optional.empty());
    }

    private static final class RecordingRunner implements TransactionalCommandRunner {
        private final TransactionalCommandContext context;
        private int calls;
        private RecordingRunner(TransactionalCommandContext context) { this.context = context; }
        @Override public <T> Uni<T> inTransaction(Function<TransactionalCommandContext, Uni<T>> work) {
            calls++;
            return work.apply(context);
        }
    }

    private static final class FakeContext implements TransactionalCommandContext {
        private final CommandIdempotencyStore.ReservationResult reservation;
        private int reserveCalls;
        private int completeCalls;
        private boolean completeResult = true;
        private String completedTenant, completedKey, completedFingerprint, completedResponse;
        private Instant completedAt;
        private FakeContext(CommandIdempotencyStore.ReservationResult reservation) { this.reservation = reservation; }
        @Override public Uni<CommandIdempotencyStore.ReservationResult> reserve(CommandIdempotencyStore.Reservation request) {
            reserveCalls++;
            assertEquals(TENANT, request.tenantId());
            assertEquals(KEY, request.key());
            assertEquals(OPERATION, request.operation());
            assertEquals(FINGERPRINT, request.requestFingerprint());
            assertEquals(NOW, request.createdAt());
            return Uni.createFrom().item(reservation);
        }
        @Override public Uni<Boolean> complete(String tenantId, String key, String requestFingerprint,
                                               String responseJson, Instant completedAt) {
            completeCalls++;
            this.completedTenant = tenantId; this.completedKey = key;
            this.completedFingerprint = requestFingerprint; this.completedResponse = responseJson;
            this.completedAt = completedAt;
            return Uni.createFrom().item(completeResult);
        }
        @Override public Uni<Optional<CommandIdempotencyStore.StoredOutcome>> find(String tenantId, String key) {
            return Uni.createFrom().item(Optional.empty());
        }
        @Override public Uni<Void> appendOutboxEvent(TransactionalOutboxEvent event) {
            return Uni.createFrom().voidItem();
        }
        @Override public BoqRevisionOperations boqRevisions() { return null; }
    }

    private static final class FakeCodec implements CommandOutcomeCodec {
        @Override public String encode(Object value) { return "json:" + value; }
        @SuppressWarnings("unchecked")
        @Override public <T> T decode(String json, Class<T> resultType) { return (T) ("decoded:" + json); }
    }
}
