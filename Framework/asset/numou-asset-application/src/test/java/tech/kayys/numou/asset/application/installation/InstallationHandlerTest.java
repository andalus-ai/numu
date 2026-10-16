package tech.kayys.numou.asset.application.installation;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.installation.AssetInstallation;
import tech.kayys.numou.asset.domain.installation.AssetInstallationId;
import tech.kayys.numou.asset.domain.model.Asset;
import tech.kayys.numou.asset.domain.relationship.AssetRelationship;
import tech.kayys.numou.asset.domain.relationship.AssetRelationshipId;
import tech.kayys.numou.asset.domain.relationship.AssetRelationshipType;
import tech.kayys.numou.asset.domain.repository.AssetInstallationRepository;
import tech.kayys.numou.asset.domain.repository.AssetRelationshipRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.asset.domain.valueobject.AssetStatus;
import tech.kayys.numou.asset.domain.valueobject.AssetType;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("Installation handlers")
class InstallationHandlerTest {

    private static final Instant NOW = Instant.parse("2026-01-10T00:00:00Z");
    private static final DomainClock CLOCK = () -> NOW;

    private FakeAssets assets;
    private FakeRelationships relationships;
    private FakeInstallations installations;
    private RecordingPublisher publisher;
    private InstallAssetComponentHandler installHandler;
    private RemoveAssetComponentHandler removeHandler;
    private GetInstallationHistoryHandler historyHandler;

    @BeforeEach
    void setUp() {
        assets = new FakeAssets();
        relationships = new FakeRelationships();
        installations = new FakeInstallations();
        publisher = new RecordingPublisher();
        DirectUow uow = new DirectUow();
        installHandler = new InstallAssetComponentHandler(assets, relationships, installations, publisher, uow, CLOCK);
        removeHandler = new RemoveAssetComponentHandler(relationships, installations, publisher, uow, CLOCK);
        historyHandler = new GetInstallationHistoryHandler(installations);
    }

    private Asset asset(String tenant, UUID id) {
        Asset a = Asset.reconstitute(AssetId.of(id), tenant, "N-" + id.toString().substring(0, 6), "Name", AssetType.EQUIPMENT, AssetStatus.ACTIVE, null, null, null);
        assets.store.put(id, a);
        return a;
    }

    @Test
    @DisplayName("install creates relationship and installation atomically")
    void installOk() {
        UUID component = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        asset("t1", component);
        asset("t1", parent);
        Result<UUID> result = installHandler.handle(new InstallAssetComponentCommand("t1", component, parent, AssetRelationshipType.COMPONENT, "TECH-1")).await().indefinitely();
        assertTrue(result.isSuccess());
        assertEquals(1, relationships.saved.size());
        assertTrue(installations.existsActive("t1", component).toCompletableFuture().join());
        assertEquals("asset.component-installed", publisher.published.get(0).eventType());
    }

    @Test
    @DisplayName("second parent is rejected and cycle is rejected")
    void rejectsSecondParentAndCycle() {
        UUID component = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        asset("t1", component);
        asset("t1", parent);
        asset("t1", other);
        installHandler.handle(new InstallAssetComponentCommand("t1", component, parent, AssetRelationshipType.COMPONENT, "T")).await().indefinitely();
        try {
            installHandler.handle(new InstallAssetComponentCommand("t1", component, other, AssetRelationshipType.COMPONENT, "T")).await().indefinitely();
            fail("expected conflict");
        } catch (Exception e) {
            assertTrue(root(e) instanceof ApplicationErrorException);
        }
        try {
            installHandler.handle(new InstallAssetComponentCommand("t1", parent, component, AssetRelationshipType.COMPONENT, "T")).await().indefinitely();
            fail("expected cycle");
        } catch (Exception e) {
            assertTrue(root(e) instanceof ApplicationErrorException);
        }
    }

    @Test
    @DisplayName("remove keeps history and second remove fails")
    void removeOk() {
        UUID component = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        asset("t1", component);
        asset("t1", parent);
        installHandler.handle(new InstallAssetComponentCommand("t1", component, parent, AssetRelationshipType.COMPONENT, "T")).await().indefinitely();
        Result<UUID> removed = removeHandler.handle(new RemoveAssetComponentCommand("t1", component, parent, "TECH-42")).await().indefinitely();
        assertTrue(removed.isSuccess());
        assertTrue(relationships.saved.isEmpty());
        List<AssetInstallation> history = historyHandler.handle(new GetInstallationHistoryQuery("t1", component)).await().indefinitely();
        assertEquals(1, history.size());
        assertEquals("REMOVED", history.get(0).status().name());
        assertEquals("asset.component-removed", publisher.published.get(1).eventType());
        try {
            removeHandler.handle(new RemoveAssetComponentCommand("t1", component, parent, "T")).await().indefinitely();
            fail("expected not-found");
        } catch (Exception e) {
            assertTrue(root(e) instanceof ApplicationErrorException);
        }
    }

    private static Throwable root(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c;
    }

    private static final class FakeAssets implements AssetRepository {
        final Map<UUID, Asset> store = new ConcurrentHashMap<>();
        @Override public CompletionStage<Asset> save(Asset a) { store.put(a.id().value(), a); return CompletableFuture.completedFuture(a); }
        @Override public CompletionStage<Optional<Asset>> findById(AssetId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id.value()))); }
        @Override public CompletionStage<Boolean> existsById(AssetId id) { return CompletableFuture.completedFuture(store.containsKey(id.value())); }
        @Override public CompletionStage<Void> delete(Asset a) { store.remove(a.id().value()); return CompletableFuture.completedFuture(null); }
        @Override public CompletionStage<Void> deleteById(AssetId id) { store.remove(id.value()); return CompletableFuture.completedFuture(null); }
        @Override public CompletionStage<Boolean> existsByAssetNumber(String t, String n) { return CompletableFuture.completedFuture(false); }
        @Override public CompletionStage<Optional<Asset>> findByTenantAndId(String t, AssetId id) {
            Asset a = store.get(id.value());
            return CompletableFuture.completedFuture(a != null && a.tenantId().equals(t) ? Optional.of(a) : Optional.empty());
        }
        @Override public CompletionStage<Boolean> existsByTenantAndId(String t, AssetId id) { return findByTenantAndId(t, id).thenApply(Optional::isPresent); }
        @Override public CompletionStage<Void> deleteByTenantAndId(String t, AssetId id) { return CompletableFuture.completedFuture(null); }
    }

    private static final class FakeRelationships implements AssetRelationshipRepository {
        final List<AssetRelationship> saved = new ArrayList<>();
        @Override public CompletionStage<AssetRelationship> save(AssetRelationship r) { saved.add(r); return CompletableFuture.completedFuture(r); }
        @Override public CompletionStage<Optional<AssetRelationship>> findById(AssetRelationshipId id) { return CompletableFuture.completedFuture(saved.stream().filter(r -> r.id().equals(id)).findFirst()); }
        @Override public CompletionStage<List<AssetRelationship>> findBySource(String t, AssetId s) { return CompletableFuture.completedFuture(saved.stream().filter(r -> r.tenantId().equals(t) && r.sourceAssetId().equals(s)).toList()); }
        @Override public CompletionStage<List<AssetRelationship>> findByTarget(String t, AssetId target) { return CompletableFuture.completedFuture(saved.stream().filter(r -> r.tenantId().equals(t) && r.relatedAssetId().equals(target)).toList()); }
        @Override public CompletionStage<Void> delete(AssetRelationship r) { saved.removeIf(x -> x.id().equals(r.id())); return CompletableFuture.completedFuture(null); }
    }

    private static final class FakeInstallations implements AssetInstallationRepository {
        final Map<UUID, AssetInstallation> store = new ConcurrentHashMap<>();
        @Override public CompletionStage<AssetInstallation> save(AssetInstallation i) { store.put(i.id().value(), i); return CompletableFuture.completedFuture(i); }
        @Override public CompletionStage<Optional<AssetInstallation>> findById(String t, AssetInstallationId id) {
            AssetInstallation i = store.get(id.value());
            return CompletableFuture.completedFuture(i != null && i.tenantId().equals(t) ? Optional.of(i) : Optional.empty());
        }
        @Override public CompletionStage<Optional<AssetInstallation>> findActive(String t, UUID c, UUID p) {
            return CompletableFuture.completedFuture(store.values().stream().filter(i -> i.tenantId().equals(t) && i.componentAssetId().equals(c) && i.parentAssetId().equals(p) && i.status().name().equals("INSTALLED")).findFirst());
        }
        @Override public CompletionStage<List<AssetInstallation>> findHistory(String t, UUID a) {
            return CompletableFuture.completedFuture(store.values().stream().filter(i -> i.tenantId().equals(t) && (i.componentAssetId().equals(a) || i.parentAssetId().equals(a))).sorted(Comparator.comparing(AssetInstallation::installedAt)).toList());
        }
        @Override public CompletionStage<Boolean> existsActive(String t, UUID c) {
            return CompletableFuture.completedFuture(store.values().stream().anyMatch(i -> i.tenantId().equals(t) && i.componentAssetId().equals(c) && i.status().name().equals("INSTALLED")));
        }
    }

    private static final class RecordingPublisher implements EventPublisher {
        final List<DomainEvent> published = new ArrayList<>();
        @Override public Uni<Void> publish(List<DomainEvent> events) { published.addAll(events); return Uni.createFrom().nullItem(); }
    }

    private static final class DirectUow implements UnitOfWork {
        @Override public <R> Uni<R> execute(Supplier<Uni<R>> work) { return work.get(); }
    }
}
