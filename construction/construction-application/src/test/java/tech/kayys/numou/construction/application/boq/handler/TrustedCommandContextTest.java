package tech.kayys.numou.construction.application.boq.handler;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.construction.application.boq.command.ApproveBoqRevisionCommand;
import tech.kayys.numou.construction.application.boq.command.CaptureBoqRevisionCommand;
import tech.kayys.numou.construction.application.boq.command.SubmitBoqRevisionForReviewCommand;
import tech.kayys.numou.construction.domain.boq.BoqCalculationPolicy;
import tech.kayys.numou.construction.domain.boq.BoqRevisionSnapshot;
import tech.kayys.numou.construction.spi.boq.BoqRevisionActorContext;
import tech.kayys.numou.construction.spi.boq.BoqRevisionTenantContext;
import tech.kayys.numou.construction.spi.boq.BoqRevisionWorkflow;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Runtime unit tests for trusted identity propagation at the command-handler boundary. */
class TrustedCommandContextTest {
    private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    private static final UUID BOQ_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final String TENANT_ID = "tenant-alpha";
    private static final BoqCalculationPolicy POLICY = new BoqCalculationPolicy("v1", 2, RoundingMode.HALF_UP);

    @Test
    void allHandlersPropagateTrustedActorAndTenantAndFixedClockTime() {
        RecordingWorkflow workflow = new RecordingWorkflow();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        BoqRevisionActorContext actor = () -> ACTOR_ID;
        BoqRevisionTenantContext tenant = () -> TENANT_ID;
        BoqRevisionSnapshot expected = snapshot();
        workflow.result = expected;

        assertSame(expected, new CaptureBoqRevisionHandler(workflow, clock, actor, tenant)
                .handle(new CaptureBoqRevisionCommand(BOQ_ID, POLICY)).await().indefinitely());
        assertEquals(new Call("capture", TENANT_ID, BOQ_ID, ACTOR_ID, 0, POLICY, NOW), workflow.calls.get(0));

        assertSame(expected, new SubmitBoqRevisionForReviewHandler(workflow, clock, actor, tenant)
                .handle(new SubmitBoqRevisionForReviewCommand(BOQ_ID, 3)).await().indefinitely());
        assertEquals(new Call("submit", TENANT_ID, BOQ_ID, ACTOR_ID, 3, null, NOW), workflow.calls.get(1));

        assertSame(expected, new ApproveBoqRevisionHandler(workflow, clock, actor, tenant)
                .handle(new ApproveBoqRevisionCommand(BOQ_ID, 3)).await().indefinitely());
        assertEquals(new Call("approve", TENANT_ID, BOQ_ID, ACTOR_ID, 3, null, NOW), workflow.calls.get(2));
    }

    @Test
    void missingTenantFailsBeforeWorkflowIsCalled() {
        RecordingWorkflow workflow = new RecordingWorkflow();
        var handler = new CaptureBoqRevisionHandler(workflow, Clock.fixed(NOW, ZoneOffset.UTC),
                () -> ACTOR_ID, () -> null);
        assertThrows(IllegalStateException.class,
                () -> handler.handle(new CaptureBoqRevisionCommand(BOQ_ID, POLICY)));
        assertTrue(workflow.calls.isEmpty());
    }

    @Test
    void blankTenantFailsBeforeWorkflowIsCalled() {
        RecordingWorkflow workflow = new RecordingWorkflow();
        var handler = new SubmitBoqRevisionForReviewHandler(workflow, Clock.fixed(NOW, ZoneOffset.UTC),
                () -> ACTOR_ID, () -> "  ");
        assertThrows(IllegalStateException.class,
                () -> handler.handle(new SubmitBoqRevisionForReviewCommand(BOQ_ID, 1)));
        assertTrue(workflow.calls.isEmpty());
    }

    @Test
    void missingActorFailsBeforeWorkflowIsCalled() {
        RecordingWorkflow workflow = new RecordingWorkflow();
        BoqRevisionActorContext noActor = () -> { throw new IllegalStateException("No authenticated actor"); };
        var handler = new ApproveBoqRevisionHandler(workflow, Clock.fixed(NOW, ZoneOffset.UTC),
                noActor, () -> TENANT_ID);
        assertThrows(IllegalStateException.class,
                () -> handler.handle(new ApproveBoqRevisionCommand(BOQ_ID, 1)));
        assertTrue(workflow.calls.isEmpty());
    }

    @Test
    void concurrentRequestsResolveTenantAndActorBeforeAsyncWorkflowBoundary() throws Exception {
        RecordingWorkflow workflow = new RecordingWorkflow();
        workflow.result = snapshot();
        ThreadLocal<String> requestTenant = new ThreadLocal<>();
        ThreadLocal<UUID> requestActor = new ThreadLocal<>();
        var handler = new CaptureBoqRevisionHandler(workflow, Clock.fixed(NOW, ZoneOffset.UTC),
                () -> {
                    UUID actor = requestActor.get();
                    if (actor == null) throw new IllegalStateException("No authenticated actor");
                    return actor;
                },
                () -> {
                    String tenant = requestTenant.get();
                    if (tenant == null || tenant.isBlank()) {
                        throw new IllegalStateException("Authenticated tenant context is required");
                    }
                    return tenant;
                });
        CountDownLatch contextsReady = new CountDownLatch(2);
        CountDownLatch invokeTogether = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> alpha = executor.submit(() -> {
                requestTenant.set("tenant-alpha");
                requestActor.set(UUID.fromString("20000000-0000-0000-0000-00000000000a"));
                contextsReady.countDown();
                if (!invokeTogether.await(5, TimeUnit.SECONDS)) throw new AssertionError("start barrier timed out");
                handler.handle(new CaptureBoqRevisionCommand(BOQ_ID, POLICY)).await().indefinitely();
                requestTenant.remove();
                requestActor.remove();
                return null;
            });
            Future<?> beta = executor.submit(() -> {
                requestTenant.set("tenant-beta");
                requestActor.set(UUID.fromString("20000000-0000-0000-0000-00000000000b"));
                contextsReady.countDown();
                if (!invokeTogether.await(5, TimeUnit.SECONDS)) throw new AssertionError("start barrier timed out");
                handler.handle(new CaptureBoqRevisionCommand(BOQ_ID, POLICY)).await().indefinitely();
                requestTenant.remove();
                requestActor.remove();
                return null;
            });
            assertTrue(contextsReady.await(5, TimeUnit.SECONDS), "both request contexts should be established");
            invokeTogether.countDown();
            alpha.get(5, TimeUnit.SECONDS);
            beta.get(5, TimeUnit.SECONDS);
            assertEquals(2, workflow.calls.size());
            assertTrue(workflow.calls.contains(new Call("capture", "tenant-alpha", BOQ_ID,
                    UUID.fromString("20000000-0000-0000-0000-00000000000a"), 0, POLICY, NOW)));
            assertTrue(workflow.calls.contains(new Call("capture", "tenant-beta", BOQ_ID,
                    UUID.fromString("20000000-0000-0000-0000-00000000000b"), 0, POLICY, NOW)));
        } finally {
            invokeTogether.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void clientCommandRecordsDoNotExposeActorOrTenantComponents() {
        assertArrayEquals(new String[]{"boqId", "calculationPolicy"},
                java.util.Arrays.stream(CaptureBoqRevisionCommand.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toArray(String[]::new));
        assertArrayEquals(new String[]{"boqId", "revisionNumber"},
                java.util.Arrays.stream(SubmitBoqRevisionForReviewCommand.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toArray(String[]::new));
        assertArrayEquals(new String[]{"boqId", "revisionNumber"},
                java.util.Arrays.stream(ApproveBoqRevisionCommand.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toArray(String[]::new));
    }

    private static BoqRevisionSnapshot snapshot() {
        return BoqRevisionSnapshot.draft(BOQ_ID, UUID.fromString("30000000-0000-0000-0000-000000000003"),
                1, "Test BOQ", List.of(), POLICY.version(), ACTOR_ID, NOW);
    }

    private record Call(String operation, String tenantId, UUID boqId, UUID actorId,
                        int revisionNumber, BoqCalculationPolicy policy, Instant now) { }

    private static final class RecordingWorkflow implements BoqRevisionWorkflow {
        private final java.util.List<Call> calls = new CopyOnWriteArrayList<>();
        private BoqRevisionSnapshot result;

        @Override public CompletionStage<BoqRevisionSnapshot> captureDraft(String tenantId, UUID boqId, UUID actorId,
                BoqCalculationPolicy calculationPolicy, Instant now) {
            calls.add(new Call("capture", tenantId, boqId, actorId, 0, calculationPolicy, now));
            return CompletableFuture.completedFuture(result);
        }
        @Override public CompletionStage<BoqRevisionSnapshot> submitForReview(String tenantId, UUID boqId,
                int revisionNumber, UUID actorId, Instant now) {
            calls.add(new Call("submit", tenantId, boqId, actorId, revisionNumber, null, now));
            return CompletableFuture.completedFuture(result);
        }
        @Override public CompletionStage<BoqRevisionSnapshot> approve(String tenantId, UUID boqId,
                int revisionNumber, UUID actorId, Instant now) {
            calls.add(new Call("approve", tenantId, boqId, actorId, revisionNumber, null, now));
            return CompletableFuture.completedFuture(result);
        }
    }
}
