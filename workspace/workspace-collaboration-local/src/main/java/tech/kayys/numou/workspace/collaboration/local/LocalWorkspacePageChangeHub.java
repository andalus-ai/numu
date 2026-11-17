package tech.kayys.numou.workspace.collaboration.local;

import tech.kayys.numou.workspace.application.port.WorkspacePageChangeNotifier;
import tech.kayys.numou.workspace.application.port.WorkspacePageChangeSignal;
import tech.kayys.numou.workspace.application.port.WorkspacePageChangeSubscription;
import tech.kayys.numou.workspace.application.port.WorkspacePageChangeSubscriptionPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
    private final int maxPerActor;
    private final Map<PageKey, Map<UUID, Registration>> registrations = new HashMap<>();
    private long publishedSignals;
    private long deliveryFailures;
    private long rejectedSubscriptions;
    private boolean closed;

    public LocalWorkspacePageChangeHub(int maxConnections, int maxPerTenant, int maxPerPage) {
        this(maxConnections, maxPerTenant, maxPerPage, maxPerTenant);
    }

    public LocalWorkspacePageChangeHub(int maxConnections, int maxPerTenant, int maxPerPage, int maxPerActor) {
        if (maxConnections < 1 || maxPerTenant < 1 || maxPerPage < 1 || maxPerActor < 1
                || maxPerTenant > maxConnections || maxPerPage > maxPerTenant || maxPerActor > maxPerTenant) {
            throw new IllegalArgumentException(
                    "limits must satisfy 1 <= maxPerPage,maxPerActor <= maxPerTenant <= maxConnections");
        }
        this.maxConnections = maxConnections;
        this.maxPerTenant = maxPerTenant;
        this.maxPerPage = maxPerPage;
        this.maxPerActor = maxPerActor;
    }

    @Override
    public synchronized WorkspacePageChangeSubscription subscribe(UUID tenantId, UUID actorId, UUID pageId,
            Consumer<WorkspacePageChangeSignal> listener) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(pageId, "pageId");
        Objects.requireNonNull(listener, "listener");
        if (closed) throw new IllegalStateException("collaboration hub is closed");

        int total = 0;
        int tenantTotal = 0;
        int actorTotal = 0;
        PageKey key = new PageKey(tenantId, pageId);
        Map<UUID, Registration> pageRegistrations = registrations.get(key);
        int pageTotal = pageRegistrations == null ? 0 : pageRegistrations.size();
        for (Map.Entry<PageKey, Map<UUID, Registration>> entry : registrations.entrySet()) {
            total += entry.getValue().size();
            if (entry.getKey().tenantId().equals(tenantId)) {
                tenantTotal += entry.getValue().size();
                for (Registration registration : entry.getValue().values()) {
                    if (registration.actorId().equals(actorId)) actorTotal++;
                }
            }
        }
        if (total >= maxConnections || tenantTotal >= maxPerTenant || pageTotal >= maxPerPage
                || actorTotal >= maxPerActor) {
            rejectedSubscriptions++;
            throw new SubscriptionLimitExceededException("workspace collaboration connection limit reached");
        }

        UUID subscriptionId = UUID.randomUUID();
        SubscriptionHandle handle = new SubscriptionHandle(key, subscriptionId);
        registrations.computeIfAbsent(key, ignored -> new HashMap<>())
                .put(subscriptionId, new Registration(actorId, listener, handle));
        return handle;
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
                registration.handle().deliver(signal, registration.listener());
            } catch (RuntimeException deliveryFailure) {
                synchronized (this) { deliveryFailures++; }
            }
        }
    }

    /** Immediately terminates all subscriptions belonging to an actor in a tenant. */
    public int revokeActor(UUID tenantId, UUID actorId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actorId, "actorId");
        List<SubscriptionHandle> revoked = new ArrayList<>();
        synchronized (this) {
            var iterator = registrations.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<PageKey, Map<UUID, Registration>> entry = iterator.next();
                if (!entry.getKey().tenantId().equals(tenantId)) continue;
                var subscriptions = entry.getValue().entrySet().iterator();
                while (subscriptions.hasNext()) {
                    Registration registration = subscriptions.next().getValue();
                    if (registration.actorId().equals(actorId)) {
                        revoked.add(registration.handle());
                        subscriptions.remove();
                    }
                }
                if (entry.getValue().isEmpty()) iterator.remove();
            }
        }
        revoked.forEach(SubscriptionHandle::revoke);
        return revoked.size();
    }

    /** Terminates all subscriptions for a tenant/page, e.g. when a page is deleted. */
    public int revokePage(UUID tenantId, UUID pageId) {
        Map<UUID, Registration> removed;
        synchronized (this) {
            removed = registrations.remove(new PageKey(
                    Objects.requireNonNull(tenantId, "tenantId"), Objects.requireNonNull(pageId, "pageId")));
        }
        if (removed == null) return 0;
        removed.values().forEach(registration -> registration.handle().revoke());
        return removed.size();
    }

    /** Terminates every subscription for a tenant, e.g. when the tenant is suspended. */
    public int revokeTenant(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        List<SubscriptionHandle> revoked = new ArrayList<>();
        synchronized (this) {
            var iterator = registrations.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<PageKey, Map<UUID, Registration>> entry = iterator.next();
                if (!entry.getKey().tenantId().equals(tenantId)) continue;
                entry.getValue().values().forEach(registration -> revoked.add(registration.handle()));
                iterator.remove();
            }
        }
        revoked.forEach(SubscriptionHandle::revoke);
        return revoked.size();
    }

    public synchronized HealthSnapshot health() {
        int total = 0;
        for (Map<UUID, Registration> page : registrations.values()) total += page.size();
        return new HealthSnapshot(total, registrations.size(), publishedSignals, deliveryFailures,
                rejectedSubscriptions, closed);
    }

    @Override
    public void close() {
        List<SubscriptionHandle> revoked = new ArrayList<>();
        synchronized (this) {
            if (closed) return;
            closed = true;
            registrations.values().forEach(page -> page.values()
                    .forEach(registration -> revoked.add(registration.handle())));
            registrations.clear();
        }
        revoked.forEach(SubscriptionHandle::revoke);
    }

    private synchronized void unsubscribe(PageKey key, UUID subscriptionId) {
        Map<UUID, Registration> page = registrations.get(key);
        if (page == null) return;
        page.remove(subscriptionId);
        if (page.isEmpty()) registrations.remove(key);
    }

    private record PageKey(UUID tenantId, UUID pageId) {}
    private record Registration(UUID actorId, Consumer<WorkspacePageChangeSignal> listener,
            SubscriptionHandle handle) {}

    private final class SubscriptionHandle implements WorkspacePageChangeSubscription {
        private final PageKey key;
        private final UUID subscriptionId;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicBoolean revoked = new AtomicBoolean();
        private final AtomicReference<Runnable> revocationCallback = new AtomicReference<>();

        private SubscriptionHandle(PageKey key, UUID subscriptionId) {
            this.key = key;
            this.subscriptionId = subscriptionId;
        }

        private synchronized void deliver(WorkspacePageChangeSignal signal,
                Consumer<WorkspacePageChangeSignal> listener) {
            if (active.get()) listener.accept(signal);
        }

        @Override
        public void onRevoked(Runnable callback) {
            Objects.requireNonNull(callback, "callback");
            boolean invoke;
            synchronized (this) {
                invoke = revoked.get();
                if (!invoke) revocationCallback.set(callback);
            }
            if (invoke) callback.run();
        }

        @Override
        public void close() {
            if (active.compareAndSet(true, false)) unsubscribe(key, subscriptionId);
        }

        private void revoke() {
            Runnable callback;
            synchronized (this) {
                if (!active.compareAndSet(true, false)) return;
                revoked.set(true);
                callback = revocationCallback.getAndSet(null);
            }
            if (callback != null) {
                try {
                    callback.run();
                } catch (RuntimeException callbackFailure) {
                    synchronized (LocalWorkspacePageChangeHub.this) { deliveryFailures++; }
                }
            }
        }
    }

    public record HealthSnapshot(int activeConnections, int activePages, long publishedSignals,
            long deliveryFailures, long rejectedSubscriptions, boolean closed) {}

    public static final class SubscriptionLimitExceededException extends RuntimeException {
        public SubscriptionLimitExceededException(String message) { super(message); }
    }
}
