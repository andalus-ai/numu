package tech.kayys.numou.workspace.collaboration.quarkus;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class QuarkusWorkspaceSseHeartbeatSchedulerTest {
    @Test void schedulesHeartbeatsAndCancelsPerConnectionTask() throws Exception {
        var scheduler = new QuarkusWorkspaceSseHeartbeatScheduler(20, TimeUnit.MILLISECONDS);
        try {
            AtomicInteger calls = new AtomicInteger();
            CountDownLatch first = new CountDownLatch(1);
            AutoCloseable registration = scheduler.schedule(() -> {
                calls.incrementAndGet();
                first.countDown();
            });
            assertTrue(first.await(2, TimeUnit.SECONDS));
            assertEquals(1, scheduler.health().activeStreams());
            registration.close();
            int stoppedAt = calls.get();
            Thread.sleep(80);
            assertEquals(stoppedAt, calls.get());
            assertEquals(0, scheduler.health().activeStreams());
        } finally {
            scheduler.close();
        }
        assertTrue(scheduler.health().closed());
        assertThrows(IllegalStateException.class, () -> scheduler.schedule(() -> {}));
    }

    @Test void isolatesCallbackFailuresAndContinuesScheduling() throws Exception {
        var scheduler = new QuarkusWorkspaceSseHeartbeatScheduler(10, TimeUnit.MILLISECONDS);
        try {
            AtomicInteger calls = new AtomicInteger();
            CountDownLatch second = new CountDownLatch(2);
            AutoCloseable registration = scheduler.schedule(() -> {
                if (calls.incrementAndGet() == 1) throw new IllegalStateException("simulated failure");
                second.countDown();
            });
            assertTrue(second.await(2, TimeUnit.SECONDS));
            assertEquals(1, scheduler.health().callbackFailures());
            registration.close();
        } finally {
            scheduler.close();
        }
    }

    @Test void rejectsUnsafeHeartbeatIntervals() {
        assertThrows(IllegalArgumentException.class, () -> new QuarkusWorkspaceSseHeartbeatScheduler(4));
        assertThrows(IllegalArgumentException.class, () -> new QuarkusWorkspaceSseHeartbeatScheduler(301));
    }
}
