package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.application.WorkspacePorts.AccessPolicy;
import tech.kayys.numou.workspace.application.WorkspacePorts.Action;
import tech.kayys.numou.workspace.application.WorkspacePorts.PageRepository;
import tech.kayys.numou.workspace.application.WorkspacePorts.Resource;
import tech.kayys.numou.workspace.application.port.WorkspacePageChangeSignal;
import tech.kayys.numou.workspace.application.port.WorkspacePageChangeSubscription;
import tech.kayys.numou.workspace.application.port.WorkspacePageChangeSubscriptionPort;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Authenticates, authorizes, and manages the lifecycle of a page-change subscription. */
public final class WorkspacePageChangeSubscriptionApi {
    private final WorkspaceApi.IdentityProvider identity;
    private final PageRepository pages;
    private final AccessPolicy access;
    private final WorkspacePageChangeSubscriptionPort subscriptions;

    public WorkspacePageChangeSubscriptionApi(WorkspaceApi.IdentityProvider identity, PageRepository pages,
            AccessPolicy access, WorkspacePageChangeSubscriptionPort subscriptions) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.pages = Objects.requireNonNull(pages, "pages");
        this.access = Objects.requireNonNull(access, "access");
        this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
    }

    public WorkspacePageChangeSubscription subscribe(UUID pageId, Consumer<WorkspacePageChangeSignal> listener) {
        Objects.requireNonNull(pageId, "pageId");
        Objects.requireNonNull(listener, "listener");
        WorkspaceApi.AuthenticatedActor actor = identity.currentActor();
        if (actor == null || actor.tenantId() == null || actor.actorId() == null) {
            throw new WorkspaceApi.ApiException(401, "An authenticated tenant and actor are required");
        }
        pages.findPage(actor.tenantId(), pageId)
                .orElseThrow(() -> new WorkspaceApi.ApiException(404, "Page not found"));
        requireRead(actor.tenantId(), actor.actorId(), pageId);

        ManagedSubscription managed = new ManagedSubscription();
        WorkspacePageChangeSubscription delegate = Objects.requireNonNull(
                subscriptions.subscribe(actor.tenantId(), actor.actorId(), pageId, signal -> {
                    if (!managed.active.get() || signal == null
                            || !actor.tenantId().equals(signal.tenantId())
                            || !pageId.equals(signal.pageId())) return;
                    try {
                        // Re-check on every signal; explicit host revocation can close idle streams
                        // without waiting for a later page mutation.
                        requireRead(actor.tenantId(), actor.actorId(), pageId);
                    } catch (WorkspaceApi.ApiException revoked) {
                        managed.revoke();
                        return;
                    }
                    listener.accept(signal);
                }), "subscription handle");
        managed.delegate.set(delegate);
        delegate.onRevoked(managed::revoke);
        if (!managed.active.get()) closeQuietly(delegate);
        return managed;
    }

    private void requireRead(UUID tenantId, UUID actorId, UUID pageId) {
        try {
            access.require(tenantId, actorId, Resource.PAGE, pageId, Action.READ);
        } catch (SecurityException denied) {
            throw new WorkspaceApi.ApiException(403, "Access denied", denied);
        }
    }

    private static void closeQuietly(AutoCloseable handle) {
        if (handle == null) return;
        try { handle.close(); } catch (Exception ignored) { /* best-effort lifecycle cleanup */ }
    }

    private static final class ManagedSubscription implements WorkspacePageChangeSubscription {
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicBoolean revoked = new AtomicBoolean();
        private final AtomicReference<WorkspacePageChangeSubscription> delegate = new AtomicReference<>();
        private final AtomicReference<Runnable> revocationCallback = new AtomicReference<>();

        @Override
        public void onRevoked(Runnable callback) {
            Objects.requireNonNull(callback, "callback");
            if (revoked.get()) {
                callback.run();
                return;
            }
            revocationCallback.set(callback);
            if (revoked.get()) {
                Runnable registered = revocationCallback.getAndSet(null);
                if (registered != null) registered.run();
            }
        }

        @Override
        public void close() {
            if (active.compareAndSet(true, false)) closeQuietly(delegate.get());
        }

        private void revoke() {
            if (!active.compareAndSet(true, false)) return;
            revoked.set(true);
            closeQuietly(delegate.get());
            Runnable callback = revocationCallback.getAndSet(null);
            if (callback != null) callback.run();
        }
    }
}
