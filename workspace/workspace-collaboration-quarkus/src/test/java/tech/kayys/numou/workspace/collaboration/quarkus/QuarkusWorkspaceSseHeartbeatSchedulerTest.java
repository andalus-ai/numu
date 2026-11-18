package tech.kayys.numou.workspace.collaboration.quarkus;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class QuarkusWorkspaceSseHeartbeatSchedulerTest {
    @Test void schedulesHeartbeatsAndCancelsPerConnectionTask() throws Exception {
        var scheduler = new QuarkusWorkspaceSseHeartbeatScheduler(5);
        try {
            AtomicInteger calls = new AtomicInteger();
            CountDownLatch first = new CountDownLatch(1);
            AutoCloseable registration = scheduler.schedule(() -> {
                calls.incrementAndGet();
                first.countDown();
            });
            assertTrue(first.await(7, TimeUnit.SECONDS));
            registration.close();
            int stoppedAt = calls.get();
            Thread.sleep(100);
            assertEquals(stoppedAt, calls.get());
        } finally {
            scheduler.close();
        }
        assertThrows(IllegalStateException.class, () -> scheduler.schedule(() -> {}));
    }

    @Test void rejectsUnsafeHeartbeatIntervals() {
        assertThrows(IllegalArgumentException.class, () -> new QuarkusWorkspaceSseHeartbeatScheduler(4));
        assertThrows(IllegalArgumentException.class, () -> new QuarkusWorkspaceSseHeartbeatScheduler(301));
    }
}
