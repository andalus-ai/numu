package tech.kayys.numou.workspace.collaboration.local;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.application.port.WorkspacePageChangeSignal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LocalWorkspacePageChangeHubTest {
    @Test void fansOutOnlyWithinTenantAndPageAndCleansUpSubscriptions() throws Exception {
        var hub = new LocalWorkspacePageChangeHub(10, 5, 3);
        UUID tenant = UUID.randomUUID(), page = UUID.randomUUID(), actor = UUID.randomUUID();
        List<WorkspacePageChangeSignal> received = new ArrayList<>();
        AutoCloseable first = hub.subscribe(tenant, actor, page, received::add);
        hub.subscribe(tenant, actor, UUID.randomUUID(), received::add);
        hub.subscribe(UUID.randomUUID(), actor, page, received::add);

        var signal = signal(tenant, page, actor);
        hub.notifyChanged(signal);
        assertEquals(1, received.size());
        assertSame(signal, received.get(0));
        first.close();
        hub.notifyChanged(signal);
        assertEquals(1, received.size());
        assertEquals(2, hub.health().activeConnections());
    }

    @Test void enforcesGlobalTenantAndPageQuotasAndCountsRejections() {
        var hub = new LocalWorkspacePageChangeHub(3, 2, 1);
        UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
        hub.subscribe(tenant, actor, UUID.randomUUID(), ignored -> {});
        assertThrows(LocalWorkspacePageChangeHub.SubscriptionLimitExceededException.class,
                () -> hub.subscribe(tenant, actor, UUID.randomUUID(), ignored -> {}));
        hub.subscribe(UUID.randomUUID(), actor, UUID.randomUUID(), ignored -> {});
        hub.subscribe(UUID.randomUUID(), actor, UUID.randomUUID(), ignored -> {});
        assertThrows(LocalWorkspacePageChangeHub.SubscriptionLimitExceededException.class,
                () -> hub.subscribe(UUID.randomUUID(), actor, UUID.randomUUID(), ignored -> {}));
        assertEquals(2, hub.health().rejectedSubscriptions());
        assertEquals(3, hub.health().activeConnections());
    }

    @Test void actorRevocationRemovesOnlyThatActorsSubscriptionsInTenant() {
        var hub = new LocalWorkspacePageChangeHub(10, 5, 5);
        UUID tenant = UUID.randomUUID(), page = UUID.randomUUID();
        UUID revoked = UUID.randomUUID(), retained = UUID.randomUUID();
        AtomicInteger deliveries = new AtomicInteger();
        hub.subscribe(tenant, revoked, page, ignored -> deliveries.incrementAndGet());
        hub.subscribe(tenant, retained, page, ignored -> deliveries.incrementAndGet());
        hub.subscribe(UUID.randomUUID(), revoked, page, ignored -> deliveries.incrementAndGet());

        assertEquals(1, hub.revokeActor(tenant, revoked));
        hub.notifyChanged(signal(tenant, page, retained));
        assertEquals(1, deliveries.get());
        assertEquals(2, hub.health().activeConnections());
    }

    @Test void isolatesListenerFailuresAndExposesHealth() {
        var hub = new LocalWorkspacePageChangeHub(10, 5, 5);
        UUID tenant = UUID.randomUUID(), page = UUID.randomUUID(), actor = UUID.randomUUID();
        hub.subscribe(tenant, actor, page, ignored -> { throw new IllegalStateException("broken consumer"); });
        AtomicInteger healthy = new AtomicInteger();
        hub.subscribe(tenant, actor, page, ignored -> healthy.incrementAndGet());
        hub.notifyChanged(signal(tenant, page, actor));
        assertEquals(1, healthy.get());
        assertEquals(1, hub.health().deliveryFailures());
        assertEquals(1, hub.health().publishedSignals());
    }

    @Test void closeRejectsNewSubscriptionsAndStopsDelivery() {
        var hub = new LocalWorkspacePageChangeHub(10, 5, 5);
        UUID tenant = UUID.randomUUID(), page = UUID.randomUUID(), actor = UUID.randomUUID();
        AtomicInteger deliveries = new AtomicInteger();
        hub.subscribe(tenant, actor, page, ignored -> deliveries.incrementAndGet());
        hub.close();
        assertThrows(IllegalStateException.class, () -> hub.subscribe(tenant, actor, page, ignored -> {}));
        hub.notifyChanged(signal(tenant, page, actor));
        assertEquals(0, deliveries.get());
        assertTrue(hub.health().closed());
        assertEquals(0, hub.health().activeConnections());
    }

    @Test void rejectsInvalidLimits() {
        assertThrows(IllegalArgumentException.class, () -> new LocalWorkspacePageChangeHub(1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new LocalWorkspacePageChangeHub(2, 1, 2));
    }


    @Test void enforcesPerPageQuotaIndependentlyOfTenantQuota() {
        var hub = new LocalWorkspacePageChangeHub(5, 3, 1);
        UUID tenant = UUID.randomUUID(), page = UUID.randomUUID();
        hub.subscribe(tenant, UUID.randomUUID(), page, ignored -> {});
        assertThrows(LocalWorkspacePageChangeHub.SubscriptionLimitExceededException.class,
                () -> hub.subscribe(tenant, UUID.randomUUID(), page, ignored -> {}));
        assertEquals(1, hub.health().activeConnections());
        assertEquals(1, hub.health().rejectedSubscriptions());
    }

    private static WorkspacePageChangeSignal signal(UUID tenant, UUID page, UUID actor) {
        return new WorkspacePageChangeSignal(tenant, UUID.randomUUID(), UUID.randomUUID(), page,
                2, actor, WorkspacePageChangeSignal.ChangeType.UPDATED, Instant.EPOCH);
    }
}
