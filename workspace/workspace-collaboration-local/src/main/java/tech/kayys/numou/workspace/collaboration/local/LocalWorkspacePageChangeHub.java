package tech.kayys.numou.workspace.collaboration.local;

import tech.kayys.numou.workspace.application.port.WorkspacePageChangeNotifier;
import tech.kayys.numou.workspace.application.port.WorkspacePageChangeSignal;
import tech.kayys.numou.workspace.application.port.WorkspacePageChangeSubscriptionPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Bounded, tenant/actor/page-scoped fanout for one process.
 *
 * <p>This adapter is suitable for standalone and modular-monolith deployments. It is deliberately
 * not a distributed broker: multi-instance hosts must provide another implementation of the
 * application ports backed by their chosen broker or shared event transport.</p>
 */
public final class LocalWorkspacePageChangeHub
        implements WorkspacePageChangeNotifier, WorkspacePageChangeSubscriptionPort, AutoCloseable {

    private final int maxConnections;
    private final int maxPerTenant;
    private final int maxPerPage;
    private final Map<PageKey, Map<UUID, Registration>> registrations = new HashMap<>();
    private long publishedSignals;
    private long deliveryFailures;
    private long rejectedSubscriptions;
    private boolean closed;

    public LocalWorkspacePageChangeHub(int maxConnections, int maxPerTenant, int maxPerPage) {
        if (maxConnections < 1 || maxPerTenant < 1 || maxPerPage < 1
                || maxPerTenant > maxConnections || maxPerPage > maxPerTenant) {
            throw new IllegalArgumentException("limits must satisfy 1 <= maxPerPage <= maxPerTenant <= maxConnections");
        }
        this.maxConnections = maxConnections;
        this.maxPerTenant = maxPerTenant;
        this.maxPerPage = maxPerPage;
    }

    @Override
    public synchronized AutoCloseable subscribe(UUID tenantId, UUID actorId, UUID pageId,
            Consumer<WorkspacePageChangeSignal> listener) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(pageId, "pageId");
        Objects.requireNonNull(listener, "listener");
        if (closed) throw new IllegalStateException("collaboration hub is closed");

        int total = 0;
        int tenantTotal = 0;
        PageKey key = new PageKey(tenantId, pageId);
        Map<UUID, Registration> pageRegistrations = registrations.get(key);
        int pageTotal = pageRegistrations == null ? 0 : pageRegistrations.size();
        for (Map.Entry<PageKey, Map<UUID, Registration>> entry : registrations.entrySet()) {
            total += entry.getValue().size();
            if (entry.getKey().tenantId().equals(tenantId)) tenantTotal += entry.getValue().size();
        }
        if (total >= maxConnections || tenantTotal >= maxPerTenant || pageTotal >= maxPerPage) {
            rejectedSubscriptions++;
            throw new SubscriptionLimitExceededException("workspace collaboration connection limit reached");
        }

        UUID subscriptionId = UUID.randomUUID();
        Registration registration = new Registration(actorId, listener);
        registrations.computeIfAbsent(key, ignored -> new HashMap<>()).put(subscriptionId, registration);
        return () -> unsubscribe(key, subscriptionId);
    }

    @Override
    public void notifyChanged(WorkspacePageChangeSignal signal) {
        Objects.requireNonNull(signal, "signal");
        List<Registration> recipients;
        synchronized (this) {
            if (closed) return;
            Map<UUID, Registration> page = registrations.get(new PageKey(signal.tenantId(), signal.pageId()));
            if (page == null || page.isEmpty()) return;
            recipients = new ArrayList<>(page.values());
            publishedSignals++;
        }
        for (Registration registration : recipients) {
            try {
                registration.listener().accept(signal);
            } catch (RuntimeException deliveryFailure) {
                synchronized (this) { deliveryFailures++; }
            }
        }
    }

    /** Immediately terminates all active subscriptions belonging to an actor in a tenant. */
    public synchronized int revokeActor(UUID tenantId, UUID actorId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actorId, "actorId");
        int removed = 0;
        var iterator = registrations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PageKey, Map<UUID, Registration>> entry = iterator.next();
            if (!entry.getKey().tenantId().equals(tenantId)) continue;
            int before = entry.getValue().size();
            entry.getValue().entrySet().removeIf(subscription -> subscription.getValue().actorId().equals(actorId));
            removed += before - entry.getValue().size();
            if (entry.getValue().isEmpty()) iterator.remove();
        }
        return removed;
    }

    /** Terminates all subscriptions for a tenant/page, for page deletion or access-policy changes. */
    public synchronized int revokePage(UUID tenantId, UUID pageId) {
        Map<UUID, Registration> removed = registrations.remove(new PageKey(
                Objects.requireNonNull(tenantId, "tenantId"), Objects.requireNonNull(pageId, "pageId")));
        return removed == null ? 0 : removed.size();
    }

    public synchronized HealthSnapshot health() {
        int total = 0;
        for (Map<UUID, Registration> page : registrations.values()) total += page.size();
        return new HealthSnapshot(total, registrations.size(), publishedSignals, deliveryFailures,
                rejectedSubscriptions, closed);
    }

    @Override
    public synchronized void close() {
        registrations.clear();
        closed = true;
    }

    private synchronized void unsubscribe(PageKey key, UUID subscriptionId) {
        Map<UUID, Registration> page = registrations.get(key);
        if (page == null) return;
        page.remove(subscriptionId);
        if (page.isEmpty()) registrations.remove(key);
    }

    private record PageKey(UUID tenantId, UUID pageId) {}
    private record Registration(UUID actorId, Consumer<WorkspacePageChangeSignal> listener) {}

    public record HealthSnapshot(int activeConnections, int activePages, long publishedSignals,
            long deliveryFailures, long rejectedSubscriptions, boolean closed) {}

    public static final class SubscriptionLimitExceededException extends RuntimeException {
        public SubscriptionLimitExceededException(String message) { super(message); }
    }
}
