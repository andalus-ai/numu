package tech.kayys.numou.history.graph.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationStore.Generation;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationStore.Status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalProjectionGenerationServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-09T07:00:00Z");

    @Test void requiresMaintenanceAuthorizationBeforeLifecycleOperations() {
        FakeStore store = new FakeStore();
        var auth = new HistoricalProjectionMaintenanceAuthorizationPort() {
            public void requireCanRebuildTenant(UUID tenant) { throw new SecurityException("denied"); }
            public void requireCanRebuildPublicCorpus() { throw new SecurityException("denied"); }
            public void requireCanAuditTenant(UUID tenant) {}
            public void requireCanAuditPublicCorpus() {}
        };
        var service = new HistoricalProjectionGenerationService(store, auth, clock());
        assertThrows(SecurityException.class, () -> service.create(UUID.randomUUID()));
        assertTrue(store.generations.isEmpty());
    }

    @Test void activationUsesExpectedActiveGenerationAndRetainsPreviousForRollback() {
        FakeStore store = new FakeStore();
        var service = service(store);
        Generation first = service.create(null);
        service.markValidatedReady(null, first.generationId());
        service.activate(null, first.generationId(), null);

        Generation second = service.create(null);
        service.markValidatedReady(null, second.generationId());
        service.activate(null, second.generationId(), first.generationId());

        assertEquals(Status.RETAINED, store.find(null, first.generationId()).orElseThrow().status());
        assertEquals(second.generationId(), service.active(null).orElseThrow().generationId());
        service.rollback(null, first.generationId(), second.generationId());
        assertEquals(Status.ACTIVE, service.active(null).orElseThrow().status());
        assertEquals(first.generationId(), service.active(null).orElseThrow().generationId());
    }

    @Test void staleActivationCannotReplaceConcurrentActiveGeneration() {
        FakeStore store = new FakeStore();
        var service = service(store);
        Generation active = service.create(null);
        service.markValidatedReady(null, active.generationId());
        service.activate(null, active.generationId(), null);
        Generation candidate = service.create(null);
        service.markValidatedReady(null, candidate.generationId());

        assertThrows(IllegalStateException.class,
                () -> service.activate(null, candidate.generationId(), UUID.randomUUID()));
        assertEquals(active.generationId(), service.active(null).orElseThrow().generationId());
    }

    private static HistoricalProjectionGenerationService service(FakeStore store) {
        var auth = new HistoricalProjectionMaintenanceAuthorizationPort() {
            public void requireCanRebuildTenant(UUID tenant) {}
            public void requireCanRebuildPublicCorpus() {}
            public void requireCanAuditTenant(UUID tenant) {}
            public void requireCanAuditPublicCorpus() {}
        };
        return new HistoricalProjectionGenerationService(store, auth, clock());
    }

    private static Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }

    private static final class FakeStore implements HistoricalProjectionGenerationStore {
        private final Map<String, Generation> generations = new HashMap<>();
        private final Map<String, UUID> active = new HashMap<>();
        private String key(UUID tenant, UUID id) { return scope(tenant) + "/" + id; }
        private String scope(UUID tenant) { return tenant == null ? "public" : tenant.toString(); }

        public Generation create(UUID tenant, UUID id, Instant now) {
            Generation g = new Generation(id, tenant, Status.BUILDING, now, now);
            if (generations.putIfAbsent(key(tenant,id), g) != null) throw new IllegalStateException();
            return g;
        }
        public Optional<Generation> find(UUID tenant, UUID id) { return Optional.ofNullable(generations.get(key(tenant,id))); }
        public Optional<Generation> active(UUID tenant) {
            UUID id = active.get(scope(tenant));
            return id == null ? Optional.empty() : find(tenant,id);
        }
        public boolean markReady(UUID tenant, UUID id, Instant now) {
            String k=key(tenant,id); Generation g=generations.get(k);
            if(g==null||g.status()!=Status.BUILDING)return false;
            generations.put(k,new Generation(id,tenant,Status.READY,g.createdAt(),now)); return true;
        }
        public boolean activate(UUID tenant, UUID id, UUID expected, Instant now) {
            String scope=scope(tenant); UUID current=active.get(scope);
            if(!java.util.Objects.equals(current,expected))return false;
            String k=key(tenant,id); Generation next=generations.get(k);
            if(next==null||(next.status()!=Status.READY&&next.status()!=Status.RETAINED))return false;
            if(current!=null) {
                String oldKey=key(tenant,current); Generation old=generations.get(oldKey);
                generations.put(oldKey,new Generation(current,tenant,Status.RETAINED,old.createdAt(),now));
            }
            generations.put(k,new Generation(id,tenant,Status.ACTIVE,next.createdAt(),now));
            active.put(scope,id); return true;
        }
    }
}
