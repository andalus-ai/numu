package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.application.WorkspacePorts.AccessPolicy;
import tech.kayys.numou.workspace.application.WorkspacePorts.Action;
import tech.kayys.numou.workspace.application.WorkspacePorts.PageRepository;
import tech.kayys.numou.workspace.application.WorkspacePorts.Resource;
import tech.kayys.numou.workspace.application.port.WorkspacePageChangeSignal;
import tech.kayys.numou.workspace.application.port.WorkspacePageChangeSubscriptionPort;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Authenticates and authorizes a page subscription before delegating to host-owned fanout. */
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

    public AutoCloseable subscribe(UUID pageId, Consumer<WorkspacePageChangeSignal> listener) {
        Objects.requireNonNull(pageId, "pageId");
        Objects.requireNonNull(listener, "listener");
        WorkspaceApi.AuthenticatedActor actor = identity.currentActor();
        if (actor == null || actor.tenantId() == null || actor.actorId() == null) {
            throw new WorkspaceApi.ApiException(401, "An authenticated tenant and actor are required");
        }
        pages.findPage(actor.tenantId(), pageId)
                .orElseThrow(() -> new WorkspaceApi.ApiException(404, "Page not found"));
        requireRead(actor.tenantId(), actor.actorId(), pageId);

        AtomicBoolean active = new AtomicBoolean(true);
        AtomicReference<AutoCloseable> delegateRef = new AtomicReference<>();
        AutoCloseable delegate = Objects.requireNonNull(
                subscriptions.subscribe(actor.tenantId(), actor.actorId(), pageId, signal -> {
                    if (!active.get() || signal == null
                            || !actor.tenantId().equals(signal.tenantId())
                            || !pageId.equals(signal.pageId())) return;
                    // Re-check on delivery so permission revocation takes effect without waiting
                    // for a new connection. A denied subscriber is closed and receives no frame.
                    try {
                        requireRead(actor.tenantId(), actor.actorId(), pageId);
                    } catch (WorkspaceApi.ApiException revoked) {
                        close(active, delegateRef.get());
                        return;
                    }
                    listener.accept(signal);
                }), "subscription handle");
        delegateRef.set(delegate);
        if (!active.get()) closeQuietly(delegate);
        return () -> close(active, delegate);
    }

    private void requireRead(UUID tenantId, UUID actorId, UUID pageId) {
        try {
            access.require(tenantId, actorId, Resource.PAGE, pageId, Action.READ);
        } catch (SecurityException denied) {
            throw new WorkspaceApi.ApiException(403, "Access denied", denied);
        }
    }

    private static void close(AtomicBoolean active, AutoCloseable delegate) {
        if (active.compareAndSet(true, false)) closeQuietly(delegate);
    }

    private static void closeQuietly(AutoCloseable handle) {
        if (handle == null) return;
        try { handle.close(); } catch (Exception ignored) { /* best-effort lifecycle cleanup */ }
    }
}
