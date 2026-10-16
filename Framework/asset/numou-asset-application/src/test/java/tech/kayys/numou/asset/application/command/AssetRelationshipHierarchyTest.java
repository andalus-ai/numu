package tech.kayys.numou.asset.application.command;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.asset.application.hierarchy.AssetRelationshipHierarchy;
import tech.kayys.numou.asset.application.query.AssetHierarchy;
import tech.kayys.numou.asset.application.query.GetAssetComponentsQuery;
import tech.kayys.numou.asset.application.query.GetAssetHierarchyHandler;
import tech.kayys.numou.asset.application.query.GetAssetHierarchyQuery;
import tech.kayys.numou.asset.application.query.GetAssetParentQuery;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.model.Asset;
import tech.kayys.numou.asset.domain.relationship.AssetRelationship;
import tech.kayys.numou.asset.domain.relationship.AssetRelationshipId;
import tech.kayys.numou.asset.domain.relationship.AssetRelationshipType;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Asset relationship integrity and hierarchy")
class AssetRelationshipHierarchyTest {

    private static final DomainClock CLOCK = () -> Instant.parse("2026-01-01T00:00:00Z");

    private FakeAssets assets;
    private FakeRelationships relationships;
    private AddAssetRelationshipHandler addHandler;
    private GetAssetHierarchyHandler hierarchyHandler;

    @BeforeEach
    void setUp() {
        assets = new FakeAssets();
        relationships = new FakeRelationships();
        addHandler = new AddAssetRelationshipHandler(assets, relationships,
                new NoopPublisher(), new DirectUnitOfWork(), CLOCK,
                new FakeHierarchyPort(relationships));
        hierarchyHandler = new GetAssetHierarchyHandler(relationships);
    }

    private AssetId asset(String tenant) {
        AssetId id = AssetId.generate();
        assets.store.put(id.value(), Asset.reconstitute(id, tenant, "N-" + id.value(),
                "Asset", AssetType.EQUIPMENT, AssetStatus.ACTIVE, null, null, null));
        return id;
    }

    private void add(AddAssetRelationshipCommand command) {
        Result<UUID> result = addHandler.handle(command).await().indefinitely();
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("A-B, B-C accepted; C-A rejected as cycle")
    void cycleRejected() {
        AssetId a = asset("t1");
        AssetId b = asset("t1");
        AssetId c = asset("t1");
        add(new AddAssetRelationshipCommand("t1", a, b, AssetRelationshipType.COMPONENT_OF));
        add(new AddAssetRelationshipCommand("t1", b, c, AssetRelationshipType.COMPONENT_OF));
        ApplicationErrorException cycle = assertThrows(ApplicationErrorException.class,
                () -> addHandler.handle(
                                new AddAssetRelationshipCommand("t1", c, a, AssetRelationshipType.COMPONENT_OF))
                        .await().indefinitely());
        assertEquals("asset.relationship.cycle", cycle.error().code());
    }

    @Test
    @DisplayName("self, duplicate and second parent rejected")
    void integrityRules() {
        AssetId a = asset("t1");
        AssetId b = asset("t1");
        AssetId d = asset("t1");
        assertThrows(ApplicationErrorException.class,
                () -> addHandler.handle(
                                new AddAssetRelationshipCommand("t1", a, a, AssetRelationshipType.COMPONENT_OF))
                        .await().indefinitely());
        add(new AddAssetRelationshipCommand("t1", a, b, AssetRelationshipType.COMPONENT_OF));
        ApplicationErrorException dup = assertThrows(ApplicationErrorException.class,
                () -> addHandler.handle(
                                new AddAssetRelationshipCommand("t1", a, b, AssetRelationshipType.COMPONENT_OF))
                        .await().indefinitely());
        assertEquals("asset.relationship.duplicate", dup.error().code());
        ApplicationErrorException parent = assertThrows(ApplicationErrorException.class,
                () -> addHandler.handle(
                                new AddAssetRelationshipCommand("t1", a, d, AssetRelationshipType.INSTALLED_ON))
                        .await().indefinitely());
        assertEquals("asset.relationship.parent-exists", parent.error().code());
    }

    @Test
    @DisplayName("non-hierarchical links skip parent and cycle checks")
    void nonHierarchicalAllowed() {
        AssetId a = asset("t1");
        AssetId b = asset("t1");
        AssetId d = asset("t1");
        add(new AddAssetRelationshipCommand("t1", a, b, AssetRelationshipType.COMPONENT_OF));
        add(new AddAssetRelationshipCommand("t1", a, d, AssetRelationshipType.RELATED_TO));
    }

    @Test
    @DisplayName("parent, components, nested hierarchy, depth and tenant isolation")
    void hierarchyQueries() {
        AssetId machine = asset("t1");
        AssetId motor = asset("t1");
        AssetId bearing = asset("t1");
        AssetId other = asset("other");
        add(new AddAssetRelationshipCommand("t1", motor, machine, AssetRelationshipType.COMPONENT_OF));
        add(new AddAssetRelationshipCommand("t1", bearing, motor, AssetRelationshipType.COMPONENT_OF));
        AssetId otherMachine = asset("other");
        add(new AddAssetRelationshipCommand("other", other, otherMachine, AssetRelationshipType.RELATED_TO));

        Optional<UUID> parent = hierarchyHandler
                .handle(new GetAssetParentQuery("t1", motor)).await().indefinitely();
        assertEquals(Optional.of(machine.value()), parent);

        List<UUID> components = hierarchyHandler
                .handle(new GetAssetComponentsQuery("t1", machine)).await().indefinitely();
        assertEquals(List.of(motor.value()), components);

        AssetHierarchy depth1 = hierarchyHandler
                .handle(new GetAssetHierarchyQuery("t1", machine, 1)).await().indefinitely();
        assertEquals(1, depth1.children().size());
        assertTrue(depth1.children().get(0).children().isEmpty());

        AssetHierarchy depth2 = hierarchyHandler
                .handle(new GetAssetHierarchyQuery("t1", machine, 2)).await().indefinitely();
        assertEquals(1, depth2.children().get(0).children().size());

        ApplicationErrorException tooDeep = assertThrows(ApplicationErrorException.class,
                () -> hierarchyHandler
                        .handle(new GetAssetHierarchyQuery("t1", machine, 11)).await().indefinitely());
        assertEquals("asset.hierarchy.depth-exceeded", tooDeep.error().code());

        List<UUID> isolated = hierarchyHandler
                .handle(new GetAssetComponentsQuery("other", machine)).await().indefinitely();
        assertTrue(isolated.isEmpty());
    }

    // ── Test doubles ─────────────────────────────────────────────────────────

    private static final class FakeAssets implements AssetRepository {

        final Map<UUID, Asset> store = new ConcurrentHashMap<>();

        @Override
        public CompletionStage<Asset> save(Asset aggregate) {
            store.put(aggregate.id().value(), aggregate);
            return CompletableFuture.completedFuture(aggregate);
        }

        @Override
        public CompletionStage<Optional<Asset>> findById(AssetId id) {
            return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id.value())));
        }

        @Override
        public CompletionStage<Boolean> existsById(AssetId id) {
            return CompletableFuture.completedFuture(store.containsKey(id.value()));
        }

        @Override
        public CompletionStage<Void> delete(Asset aggregate) {
            store.remove(aggregate.id().value());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> deleteById(AssetId id) {
            store.remove(id.value());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Boolean> existsByAssetNumber(String tenantId, String assetNumber) {
            return CompletableFuture.completedFuture(store.values().stream()
                    .anyMatch(a -> a.tenantId().equals(tenantId)
                            && a.assetNumber().equals(assetNumber)));
        }

        @Override
        public CompletionStage<Optional<Asset>> findByTenantAndId(String tenantId, AssetId id) {
            Asset asset = store.get(id.value());
            boolean visible = asset != null && asset.tenantId().equals(tenantId);
            return CompletableFuture.completedFuture(
                    visible ? Optional.of(asset) : Optional.empty());
        }

        @Override
        public CompletionStage<Boolean> existsByTenantAndId(String tenantId, AssetId id) {
            return findByTenantAndId(tenantId, id).thenApply(Optional::isPresent);
        }

        @Override
        public CompletionStage<Void> deleteByTenantAndId(String tenantId, AssetId id) {
            Asset asset = store.get(id.value());
            if (asset != null && asset.tenantId().equals(tenantId)) {
                store.remove(id.value());
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class FakeRelationships implements AssetRelationshipRepository {

        final List<AssetRelationship> saved = new ArrayList<>();

        @Override
        public CompletionStage<AssetRelationship> save(AssetRelationship relationship) {
            saved.add(relationship);
            return CompletableFuture.completedFuture(relationship);
        }

        @Override
        public CompletionStage<Optional<AssetRelationship>> findById(AssetRelationshipId id) {
            return CompletableFuture.completedFuture(
                    saved.stream().filter(r -> r.id().equals(id)).findFirst());
        }

        @Override
        public CompletionStage<List<AssetRelationship>> findBySource(String tenantId, AssetId sourceAssetId) {
            return CompletableFuture.completedFuture(saved.stream()
                    .filter(r -> r.tenantId().equals(tenantId)
                            && r.sourceAssetId().equals(sourceAssetId))
                    .toList());
        }

        @Override
        public CompletionStage<List<AssetRelationship>> findByTarget(String tenantId, AssetId targetAssetId) {
            return CompletableFuture.completedFuture(saved.stream()
                    .filter(r -> r.tenantId().equals(tenantId)
                            && r.relatedAssetId().equals(targetAssetId))
                    .toList());
        }

        @Override
        public CompletionStage<Void> delete(AssetRelationship relationship) {
            saved.removeIf(r -> r.id().equals(relationship.id()));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class FakeHierarchyPort implements AssetRelationshipHierarchy {

        private final AssetRelationshipRepository relationships;

        private FakeHierarchyPort(AssetRelationshipRepository relationships) {
            this.relationships = relationships;
        }

        @Override
        public CompletionStage<Boolean> wouldCreateCycle(
                String tenantId, UUID sourceAssetId, UUID targetAssetId) {
            return descend(tenantId, sourceAssetId, targetAssetId, 0);
        }

        /**
         * Walks <em>down</em> the hierarchical graph from {@code from}: children of X are
         * relationships whose target is X (source = child). A cycle exists when
         * {@code target} is already a descendant of {@code source}.
         */
        private CompletionStage<Boolean> descend(
                String tenantId, UUID from, UUID target, int depth) {
            if (depth > MAX_TRAVERSAL_DEPTH) {
                return CompletableFuture.completedFuture(false);
            }
            return relationships.findByTarget(tenantId, AssetId.of(from))
                    .thenCompose(children -> {
                        if (children.stream().anyMatch(
                                c -> c.sourceAssetId().value().equals(target))) {
                            return CompletableFuture.completedFuture(Boolean.TRUE);
                        }
                        CompletableFuture<Boolean> found =
                                CompletableFuture.completedFuture(Boolean.FALSE);
                        for (AssetRelationship child : children) {
                            UUID childId = child.sourceAssetId().value();
                            found = found.thenCompose(hit -> hit
                                    ? CompletableFuture.completedFuture(Boolean.TRUE)
                                    : descend(tenantId, childId, target, depth + 1));
                        }
                        return found;
                    });
        }
    }

    private static final class NoopPublisher implements EventPublisher {

        final List<DomainEvent> published = new ArrayList<>();

        @Override
        public Uni<Void> publish(List<DomainEvent> events) {
            published.addAll(events);
            return Uni.createFrom().nullItem();
        }
    }

    private static final class DirectUnitOfWork implements UnitOfWork {

        @Override
        public <R> Uni<R> execute(Supplier<Uni<R>> work) {
            return work.get();
        }
    }
}
