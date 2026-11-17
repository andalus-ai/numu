package tech.kayys.numou.workspace.adapter.rest;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Bounded lifecycle manager for SSE connections. It owns only transport lifecycle; page
 * authorization and event fanout remain in the Workspace application ports.
 */
@ApplicationScoped
public class WorkspaceSseConnectionRegistry implements AutoCloseable {
    private final int maxConnections;
    private final Duration heartbeatInterval;
    private final Duration writeTimeout;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;
    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final LongAdder accepted = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder heartbeats = new LongAdder();
    private final LongAdder timedOut = new LongAdder();
    private final LongAdder slowConsumers = new LongAdder();
    private final LongAdder sendFailures = new LongAdder();
    private final Object admissionLock = new Object();

    /** CDI default: bounded process-local transport lifecycle with conservative defaults. */
    public WorkspaceSseConnectionRegistry() {
        this(2_000, Duration.ofSeconds(20), Duration.ofSeconds(60),
                Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread thread = new Thread(task, "workspace-sse-heartbeats");
                    thread.setDaemon(true);
                    return thread;
                }), Clock.systemUTC(), true);
    }

    WorkspaceSseConnectionRegistry(int maxConnections, Duration heartbeatInterval, Duration writeTimeout,
            ScheduledExecutorService scheduler, Clock clock) {
        this(maxConnections, heartbeatInterval, writeTimeout, scheduler, clock, false);
    }

    private WorkspaceSseConnectionRegistry(int maxConnections, Duration heartbeatInterval, Duration writeTimeout,
            ScheduledExecutorService scheduler, Clock clock, boolean ownsScheduler) {
        if (maxConnections < 1) throw new IllegalArgumentException("maxConnections must be positive");
        if (heartbeatInterval == null || heartbeatInterval.isZero() || heartbeatInterval.isNegative())
            throw new IllegalArgumentException("heartbeatInterval must be positive");
        if (writeTimeout == null || writeTimeout.compareTo(heartbeatInterval) <= 0)
            throw new IllegalArgumentException("writeTimeout must exceed heartbeatInterval");
        this.maxConnections = maxConnections;
        this.heartbeatInterval = heartbeatInterval;
        this.writeTimeout = writeTimeout;
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.ownsScheduler = ownsScheduler;
    }

    public Connection open(Supplier<? extends CompletionStage<?>> heartbeatWriter, Runnable closeWriter) {
        java.util.Objects.requireNonNull(heartbeatWriter, "heartbeatWriter");
        java.util.Objects.requireNonNull(closeWriter, "closeWriter");
        synchronized (admissionLock) {
            if (closed.get()) throw new IllegalStateException("SSE connection registry is closed");
            if (active.get() >= maxConnections) {
                rejected.increment();
                throw new ConnectionLimitExceededException("workspace SSE connection limit reached");
            }
            Connection connection = new Connection(heartbeatWriter, closeWriter);
            connections.add(connection);
            active.incrementAndGet();
            accepted.increment();
            connection.start();
            return connection;
        }
    }

    public HealthSnapshot health() {
        return new HealthSnapshot(active.get(), accepted.sum(), rejected.sum(), heartbeats.sum(),
                timedOut.sum(), slowConsumers.sum(), sendFailures.sum(), closed.get());
    }

    @Override
    @PreDestroy
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        for (Connection connection : Set.copyOf(connections)) connection.close();
        if (ownsScheduler) scheduler.shutdownNow();
    }

    private void released(Connection connection) {
        connections.remove(connection);
        active.decrementAndGet();
    }

    public final class Connection implements AutoCloseable {
        private final Supplier<? extends CompletionStage<?>> heartbeatWriter;
        private final Runnable closeWriter;
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final AtomicBoolean writing = new AtomicBoolean();
        private final AtomicBoolean cleanupRun = new AtomicBoolean();
        private final AtomicReference<Runnable> cleanup = new AtomicReference<>();
        private final AtomicLong lastSuccessfulWrite = new AtomicLong(clock.millis());
        private volatile ScheduledFuture<?> heartbeatTask;

        private Connection(Supplier<? extends CompletionStage<?>> heartbeatWriter, Runnable closeWriter) {
            this.heartbeatWriter = heartbeatWriter;
            this.closeWriter = closeWriter;
        }

        private void start() {
            heartbeatTask = scheduler.scheduleAtFixedRate(this::heartbeat,
                    heartbeatInterval.toMillis(), heartbeatInterval.toMillis(), TimeUnit.MILLISECONDS);
        }

        /** Attach the subscription cleanup after registration; handles close-before-attach races. */
        public void onClose(Runnable cleanupAction) {
            cleanup.set(java.util.Objects.requireNonNull(cleanupAction, "cleanupAction"));
            if (!open.get()) runCleanup();
        }

        /** Send a page event without allowing an unbounded per-connection write queue. */
        public boolean send(Supplier<? extends CompletionStage<?>> writer) {
            return send(writer, false);
        }

        private boolean send(Supplier<? extends CompletionStage<?>> writer, boolean heartbeat) {
            if (!open.get()) return false;
            if (!writing.compareAndSet(false, true)) {
                if (heartbeat) {
                    if (clock.millis() - lastSuccessfulWrite.get() > writeTimeout.toMillis()) {
                        timedOut.increment();
                        close();
                    }
                    return false;
                }
                slowConsumers.increment();
                close();
                return false;
            }
            CompletionStage<?> stage;
            try {
                stage = java.util.Objects.requireNonNull(writer.get(), "SSE send stage");
            } catch (RuntimeException failure) {
                writing.set(false);
                sendFailures.increment();
                close();
                return false;
            }
            stage.whenComplete((ignored, failure) -> {
                writing.set(false);
                if (failure != null) {
                    sendFailures.increment();
                    close();
                } else if (open.get()) {
                    lastSuccessfulWrite.set(clock.millis());
                    if (heartbeat) heartbeats.increment();
                }
            });
            return true;
        }

        private void heartbeat() {
            if (!open.get()) return;
            if (clock.millis() - lastSuccessfulWrite.get() > writeTimeout.toMillis()) {
                timedOut.increment();
                close();
                return;
            }
            send(heartbeatWriter, true);
        }

        @Override
        public void close() {
            if (!open.compareAndSet(true, false)) return;
            ScheduledFuture<?> task = heartbeatTask;
            if (task != null) task.cancel(false);
            released(this);
            runCleanup();
            try {
                closeWriter.run();
            } catch (RuntimeException ignored) {
                // Connection cleanup must continue even if the transport sink is already closed.
            }
        }

        private void runCleanup() {
            Runnable action = cleanup.get();
            if (action != null && cleanupRun.compareAndSet(false, true)) {
                try { action.run(); } catch (RuntimeException ignored) { /* best-effort cleanup */ }
            }
        }
    }

    public record HealthSnapshot(int activeConnections, long acceptedConnections, long rejectedConnections,
            long heartbeatsSent, long writeTimeouts, long slowConsumerDisconnects, long sendFailures,
            boolean closed) {}

    public static final class ConnectionLimitExceededException extends RuntimeException {
        public ConnectionLimitExceededException(String message) { super(message); }
    }
}
