package tech.kayys.numou.workspace.application.port;

/**
 * Optional non-blocking invalidation channel for page collaboration. Implementations may fan
 * out through an in-process bus, SSE/WebSocket gateway, or multi-instance broker. They must
 * not persist page content, bypass tenant subscriptions, or turn a committed page write into
 * a failed command when a signal cannot be delivered. Delivery is advisory; clients recover
 * missed signals through the durable M35 revision cursor. Implementations should expose
 * delivery failures through host metrics and operational telemetry.
 */
@FunctionalInterface
public interface WorkspacePageChangeNotifier {
    void notifyChanged(WorkspacePageChangeSignal signal);
}
