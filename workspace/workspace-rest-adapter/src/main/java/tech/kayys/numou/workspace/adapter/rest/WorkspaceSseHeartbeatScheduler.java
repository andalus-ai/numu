package tech.kayys.numou.workspace.adapter.rest;

/**
 * Host-owned scheduler for SSE comment heartbeats. The returned handle must cancel the recurring
 * task promptly when the HTTP connection closes. Implementations must share scheduler threads
 * across connections rather than allocating one thread per stream.
 */
@FunctionalInterface
public interface WorkspaceSseHeartbeatScheduler {
    AutoCloseable schedule(Runnable heartbeat);
}
