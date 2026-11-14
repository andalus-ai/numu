package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.application.WorkspacePorts.*;
import tech.kayys.numou.workspace.application.port.*;
import tech.kayys.numou.workspace.domain.Page;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class WorkspacePageChangeSubscriptionApiTest {
    @Test void authorizesBeforeRegisteringAndFiltersSignalsToTenantAndPage() {
        UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID(), pageId = UUID.randomUUID();
        Page page = new Page(pageId, tenant, UUID.randomUUID(), UUID.randomUUID(), null,
                "Page", List.of(), 1, Instant.EPOCH, Instant.EPOCH);
        AtomicInteger authorizationCalls = new AtomicInteger();
        @SuppressWarnings("unchecked")
        Consumer<WorkspacePageChangeSignal>[] registered = new Consumer[1];
        AtomicInteger closeCalls = new AtomicInteger();
        WorkspacePageChangeSubscriptionApi api = new WorkspacePageChangeSubscriptionApi(
                () -> new WorkspaceApi.AuthenticatedActor(tenant, actor),
                repository(tenant, page),
                (t, a, resource, id, action) -> {
                    authorizationCalls.incrementAndGet();
                    assertEquals(tenant, t); assertEquals(actor, a);
                    assertEquals(Resource.PAGE, resource); assertEquals(pageId, id); assertEquals(Action.READ, action);
                },
                (t, subscribedActor, id, listener) -> {
                    assertEquals(tenant, t); assertEquals(actor, subscribedActor); assertEquals(pageId, id);
                    registered[0] = listener;
                    return closeCalls::incrementAndGet;
                });
        List<WorkspacePageChangeSignal> received = new ArrayList<>();
        AutoCloseable subscription = api.subscribe(pageId, received::add);
        assertEquals(1, authorizationCalls.get());
        WorkspacePageChangeSignal valid = signal(tenant, pageId);
        registered[0].accept(signal(UUID.randomUUID(), pageId));
        registered[0].accept(signal(tenant, UUID.randomUUID()));
        registered[0].accept(valid);
        assertEquals(List.of(valid), received);
        assertDoesNotThrow(subscription::close);
        assertEquals(1, closeCalls.get());
        registered[0].accept(valid);
        assertEquals(1, received.size(), "closed subscriptions must not deliver");
    }

    @Test void deniedOrMissingPageNeverRegistersSubscription() {
        UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID(), pageId = UUID.randomUUID();
        AtomicInteger registrations = new AtomicInteger();
        Page page = new Page(pageId, tenant, UUID.randomUUID(), UUID.randomUUID(), null,
                "Page", List.of(), 1, Instant.EPOCH, Instant.EPOCH);
        WorkspacePageChangeSubscriptionApi denied = new WorkspacePageChangeSubscriptionApi(
                () -> new WorkspaceApi.AuthenticatedActor(tenant, actor), repository(tenant, page),
                (t, a, r, id, action) -> { throw new SecurityException("denied"); },
                (t, id, listener) -> { registrations.incrementAndGet(); return () -> {}; });
        assertEquals(403, assertThrows(WorkspaceApi.ApiException.class,
                () -> denied.subscribe(pageId, signal -> {})).status());
        assertEquals(0, registrations.get());

        WorkspacePageChangeSubscriptionApi missing = new WorkspacePageChangeSubscriptionApi(
                () -> new WorkspaceApi.AuthenticatedActor(tenant, actor), repository(tenant, page, true), (t, a, r, id, action) -> {}, (t, id, listener) -> {
                    registrations.incrementAndGet(); return () -> {};
                });
        assertEquals(404, assertThrows(WorkspaceApi.ApiException.class,
                () -> missing.subscribe(pageId, signal -> {})).status());
        assertEquals(0, registrations.get());
    }

    @Test void missingTrustedIdentityIsRejected() {
        WorkspacePageChangeSubscriptionApi api = new WorkspacePageChangeSubscriptionApi(
                () -> null, repository(UUID.randomUUID(), null), (t, a, r, id, action) -> {},
                (t, id, listener) -> { fail("must not subscribe"); return () -> {}; });
        assertEquals(401, assertThrows(WorkspaceApi.ApiException.class,
                () -> api.subscribe(UUID.randomUUID(), signal -> {})).status());
    }

    private static PageRepository repository(UUID tenant, Page page) { return repository(tenant, page, false); }

    private static PageRepository repository(UUID tenant, Page page, boolean forceMissing) {
        return new PageRepository() {
            public Optional<Page> findPage(UUID t, UUID id) {
                return !forceMissing && page != null && tenant.equals(t) && page.id().equals(id) ? Optional.of(page) : Optional.empty();
            }
            public List<Page> findChildren(UUID t, UUID s, UUID parent) { return List.of(); }
            public boolean saveWithRevision(Page p, tech.kayys.numou.workspace.domain.PageRevision r, long expected) {
                return false;
            }
            public List<tech.kayys.numou.workspace.domain.PageRevision> history(UUID t, UUID id) { return List.of(); }
        };
    }

    private static WorkspacePageChangeSignal signal(UUID tenant, UUID pageId) {
        return new WorkspacePageChangeSignal(tenant, UUID.randomUUID(), UUID.randomUUID(), pageId,
                2, UUID.randomUUID(), WorkspacePageChangeSignal.ChangeType.UPDATED, Instant.EPOCH);
    }
}
