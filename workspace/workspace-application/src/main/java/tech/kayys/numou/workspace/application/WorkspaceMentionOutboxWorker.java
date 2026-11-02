package tech.kayys.numou.workspace.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduler-neutral worker entry point. The host owns scheduling and shutdown;
 * this guard prevents overlapping invocations within one application instance.
 */
public final class WorkspaceMentionOutboxWorker {
    private final WorkspaceMentionOutboxDispatcher dispatcher;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile RunSnapshot lastRun;

    public WorkspaceMentionOutboxWorker(WorkspaceMentionOutboxDispatcher dispatcher, Clock clock) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RunSnapshot runOnce(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be 1..500");
        if (!running.compareAndSet(false, true)) {
            return new RunSnapshot(false, null, clock.instant(), null);
        }
        Instant started = clock.instant();
        try {
            WorkspaceMentionOutboxDispatcher.DispatchResult result = dispatcher.dispatch(limit);
            RunSnapshot completed = new RunSnapshot(true, started, clock.instant(), result);
            lastRun = completed;
            return completed;
        } finally {
            running.set(false);
        }
    }

    public boolean isRunning() { return running.get(); }

    public RunSnapshot lastRun() { return lastRun; }

    public record RunSnapshot(boolean executed, Instant startedAt, Instant finishedAt,
                              WorkspaceMentionOutboxDispatcher.DispatchResult dispatch) { }
}
