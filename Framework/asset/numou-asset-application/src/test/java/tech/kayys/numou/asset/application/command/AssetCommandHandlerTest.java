package tech.kayys.numou.asset.application.command;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.model.Asset;
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

/**
 * Tenant-isolation and event-publication tests for the Asset write use cases
 * (ASSET-06 / ASSET-08 / ASSET-11, plan sections 37-39).
 */
@DisplayName("Asset command handlers")
class AssetCommandHandlerTest {

    private static final DomainClock CLOCK = () -> Instant.parse("2026-01-01T00:00:00Z");

    private InMemoryAssetRepository repository;
    private RecordingEventPublisher eventPublisher;
    private CreateAssetHandler createHandler;
    private ActivateAssetHandler activateHandler;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAssetRepository();
        eventPublisher = new RecordingEventPublisher();
        DirectUnitOfWork unitOfWork = new DirectUnitOfWork();

        createHandler = new CreateAssetHandler(repository, eventPublisher, unitOfWork, CLOCK);
        activateHandler = new ActivateAssetHandler(repository, eventPublisher, unitOfWork, CLOCK);
    }

    @Test
    @DisplayName("create persists a DRAFT asset and publishes AssetCreated")
    void createPublishesAssetCreated() {
        Result<CreateAssetResult> result = createHandler
                .handle(new CreateAssetCommand("tenant-a", "AST-1", "Truck", AssetType.VEHICLE))
                .await().indefinitely();

        assertTrue(result.isSuccess());
        assertEquals("AST-1", result.orElseThrow().assetNumber());
        assertEquals(1, eventPublisher.published.size());
        assertEquals("asset.asset-created", eventPublisher.published.get(0).eventType());
    }

    @Test
    @DisplayName("duplicate asset number within a tenant is rejected")
    void rejectsDuplicateAssetNumber() {
        createHandler.handle(new CreateAssetCommand("tenant-a", "AST-1", "Truck", AssetType.VEHICLE))
                .await().indefinitely();

        Result<CreateAssetResult> duplicate = createHandler
                .handle(new CreateAssetCommand("tenant-a", "AST-1", "Other", AssetType.VEHICLE))
                .await().indefinitely();

        assertTrue(duplicate.isFailure());
    }

    @Test
    @DisplayName("a tenant cannot read or activate another tenant's asset")
    void enforcesTenantIsolation() {
        Result<CreateAssetResult> created = createHandler
                .handle(new CreateAssetCommand("tenant-a", "AST-1", "Truck", AssetType.VEHICLE))
                .await().indefinitely();
        UUID assetId = created.orElseThrow().assetId();

        assertThrows(ApplicationErrorException.class, () ->
                activateHandler.handle(new ActivateAssetCommand("tenant-b", AssetId.of(assetId)))
                        .await().indefinitely());

        activateHandler.handle(new ActivateAssetCommand("tenant-a", AssetId.of(assetId)))
                .await().indefinitely();

        assertEquals(AssetStatus.ACTIVE, repository.store.get(assetId).status());
    }

    // ── Test doubles ─────────────────────────────────────────────────────────

    private static final class InMemoryAssetRepository implements AssetRepository {

        private final Map<UUID, Asset> store = new ConcurrentHashMap<>();

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
                    .anyMatch(asset -> asset.tenantId().equals(tenantId)
                            && asset.assetNumber().equals(assetNumber)));
        }

        @Override
        public CompletionStage<Optional<Asset>> findByTenantAndId(String tenantId, AssetId assetId) {
            Asset asset = store.get(assetId.value());
            boolean visible = asset != null && asset.tenantId().equals(tenantId);
            return CompletableFuture.completedFuture(
                    visible ? Optional.of(asset) : Optional.empty());
        }

        @Override
        public CompletionStage<Boolean> existsByTenantAndId(String tenantId, AssetId assetId) {
            return findByTenantAndId(tenantId, assetId).thenApply(Optional::isPresent);
        }

        @Override
        public CompletionStage<Void> deleteByTenantAndId(String tenantId, AssetId assetId) {
            Asset asset = store.get(assetId.value());
            if (asset != null && asset.tenantId().equals(tenantId)) {
                store.remove(assetId.value());
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingEventPublisher implements EventPublisher {

        private final List<DomainEvent> published = new ArrayList<>();

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
