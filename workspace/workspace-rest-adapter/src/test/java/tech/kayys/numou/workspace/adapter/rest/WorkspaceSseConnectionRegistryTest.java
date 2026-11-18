package tech.kayys.numou.workspace.adapter.rest;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceSseConnectionRegistryTest {
    @Test
    void enforcesConnectionCapacityAndClosesIdempotently() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        WorkspaceSseConnectionRegistry registry = registry(1, Duration.ofSeconds(1), Duration.ofSeconds(3), scheduler);
        AtomicInteger closed = new AtomicInteger();
        try {
            var connection = registry.open(() -> CompletableFuture.completedFuture(null), closed::incrementAndGet);
            assertThrows(WorkspaceSseConnectionRegistry.ConnectionLimitExceededException.class,
                    () -> registry.open(() -> CompletableFuture.completedFuture(null), () -> {}));
            assertEquals(1, registry.health().activeConnections());
            assertEquals(1, registry.health().rejectedConnections());

            connection.close();
            connection.close();
            AtomicInteger lateCleanup = new AtomicInteger();
            connection.onClose(lateCleanup::incrementAndGet);
            assertEquals(1, lateCleanup.get(), "cleanup attached after close must run immediately");
            assertEquals(1, closed.get());
            assertEquals(0, registry.health().activeConnections());
        } finally {
            registry.close();
            scheduler.shutdownNow();
        }
    }

    @Test
    void sendsPeriodicHeartbeatsAndShutsDownActiveConnections() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        WorkspaceSseConnectionRegistry registry = registry(2, Duration.ofMillis(10), Duration.ofMillis(100), scheduler);
        CountDownLatch heartbeat = new CountDownLatch(1);
        AtomicInteger closed = new AtomicInteger();
        try {
            registry.open(() -> {
                heartbeat.countDown();
                return CompletableFuture.completedFuture(null);
            }, closed::incrementAndGet);
            assertTrue(heartbeat.await(1, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (registry.health().heartbeatsSent() < 1 && System.nanoTime() < deadline) {
                Thread.sleep(2);
            }
            assertTrue(registry.health().heartbeatsSent() >= 1);
            registry.close();
            assertEquals(0, registry.health().activeConnections());
            assertEquals(1, closed.get());
            assertTrue(registry.health().closed());
        } finally {
            registry.close();
            scheduler.shutdownNow();
        }
    }

    @Test
    void disconnectsAWriterThatDoesNotCompleteWithinTimeout() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        WorkspaceSseConnectionRegistry registry = registry(1, Duration.ofMillis(10), Duration.ofMillis(50), scheduler);
        CountDownLatch firstHeartbeat = new CountDownLatch(1);
        CountDownLatch disconnected = new CountDownLatch(1);
        try {
            registry.open(() -> {
                firstHeartbeat.countDown();
                return new CompletableFuture<>();
            }, disconnected::countDown);
            assertTrue(firstHeartbeat.await(1, TimeUnit.SECONDS));
            assertTrue(disconnected.await(1, TimeUnit.SECONDS));
            assertEquals(1, registry.health().writeTimeouts());
            assertEquals(0, registry.health().activeConnections());
        } finally {
            registry.close();
            scheduler.shutdownNow();
        }
    }

    @Test
    void disconnectsConcurrentSlowConsumerWritesAndRecordsFailures() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        WorkspaceSseConnectionRegistry registry = registry(2, Duration.ofSeconds(1), Duration.ofSeconds(3), scheduler);
        AtomicInteger closed = new AtomicInteger();
        try {
            var slow = registry.open(() -> CompletableFuture.completedFuture(null), closed::incrementAndGet);
            var pending = new CompletableFuture<Void>();
            assertTrue(slow.send(() -> pending));
            assertFalse(slow.send(() -> CompletableFuture.completedFuture(null)));
            assertEquals(1, registry.health().slowConsumerDisconnects());

            var failed = registry.open(() -> CompletableFuture.completedFuture(null), closed::incrementAndGet);
            assertTrue(failed.send(() -> CompletableFuture.failedFuture(new IllegalStateException("send failed"))));
            assertEquals(1, registry.health().sendFailures());
            assertEquals(2, closed.get());
            assertEquals(0, registry.health().activeConnections());
        } finally {
            registry.close();
            scheduler.shutdownNow();
        }
    }

    private static WorkspaceSseConnectionRegistry registry(int max, Duration heartbeat, Duration timeout,
            ScheduledExecutorService scheduler) {
        return new WorkspaceSseConnectionRegistry(max, heartbeat, timeout, scheduler, Clock.systemUTC());
    }
}
