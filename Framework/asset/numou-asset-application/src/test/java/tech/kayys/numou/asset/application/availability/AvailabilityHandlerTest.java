package tech.kayys.numou.asset.application.availability;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.asset.application.command.ActivateAssetCommand;
import tech.kayys.numou.asset.application.command.ActivateAssetHandler;
import tech.kayys.numou.asset.application.command.CreateAssetCommand;
import tech.kayys.numou.asset.application.command.CreateAssetHandler;
import tech.kayys.numou.asset.application.command.DisposeAssetCommand;
import tech.kayys.numou.asset.application.command.DisposeAssetHandler;
import tech.kayys.numou.asset.application.command.RetireAssetCommand;
import tech.kayys.numou.asset.application.command.RetireAssetHandler;
import tech.kayys.numou.asset.domain.availability.AssetAvailabilityPeriod;
import tech.kayys.numou.asset.domain.availability.AssetAvailabilityPeriodId;
import tech.kayys.numou.asset.domain.availability.AssetAvailabilityReason;
import tech.kayys.numou.asset.domain.availability.AssetAvailabilityType;
import tech.kayys.numou.asset.domain.availability.AssetUtilizationRecord;
import tech.kayys.numou.asset.domain.availability.AssetUtilizationType;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.model.Asset;
import tech.kayys.numou.asset.domain.repository.AssetAvailabilityRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.asset.domain.repository.AssetUtilizationRepository;
import tech.kayys.numou.asset.domain.valueobject.AssetType;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.math.BigDecimal;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance tests for ASSET-26 availability &amp; utilization use cases:
 * lifecycle guard, overlap conflict, unavailability override, and keyed
 * idempotency for external utilization ingestion.
 */
@DisplayName("Availability & utilization handlers")
class AvailabilityHandlerTest {

    private static final DomainClock CLOCK = () -> Instant.parse("2026-10-03T09:00:00Z");
    private static final Instant T08 = Instant.parse("2026-10-03T08:00:00Z");
    private static final Instant T10 = Instant.parse("2026-10-03T10:00:00Z");
    private static final Instant T12 = Instant.parse("2026-10-03T12:00:00Z");
    private static final Instant T17 = Instant.parse("2026-10-03T17:00:00Z");

    private InMemoryAssetRepository assets;
    private FakeAvailabilityRepository availability;
    private FakeUtilizationRepository utilization;
    private MarkAssetAvailabilityHandler markHandler;
    private GetAssetAvailabilityHandler viewHandler;
    private RecordAssetUtilizationHandler utilizationHandler;

    @BeforeEach
    void setUp() {
        assets = new InMemoryAssetRepository();
        availability = new FakeAvailabilityRepository();
        utilization = new FakeUtilizationRepository();
        UnitOfWork uow = new DirectUnitOfWork();
        EventPublisher publisher = new NoopEventPublisher();
        markHandler = new MarkAssetAvailabilityHandler(assets, availability, publisher, uow, CLOCK);
        viewHandler = new GetAssetAvailabilityHandler(assets, availability, CLOCK);
        utilizationHandler = new RecordAssetUtilizationHandler(assets, utilization, publisher, uow, CLOCK);
    }

    private UUID activeAsset(String tenantId, String number) {
        EventPublisher publisher = new NoopEventPublisher();
        UnitOfWork uow = new DirectUnitOfWork();
        UUID id = new CreateAssetHandler(assets, publisher, uow, CLOCK)
                .handle(new CreateAssetCommand(tenantId, number, "Asset " + number, AssetType.VEHICLE))
                .await().indefinitely().orElseThrow().assetId();
        new ActivateAssetHandler(assets, publisher, uow, CLOCK)
                .handle(new ActivateAssetCommand(tenantId, AssetId.of(id))).await().indefinitely();
        return id;
    }

    @Test
    @DisplayName("an available window makes the asset available")
    void marksAvailable() {
        UUID id = activeAsset("tenant-a", "AST-1");
        Result<AvailabilityResult> result = markHandler.handle(new MarkAssetAvailabilityCommand(
                "tenant-a", id, AssetAvailabilityType.AVAILABLE, AssetAvailabilityReason.NORMAL_OPERATION,
                T08, T17, null, null)).await().indefinitely();
        assertTrue(result.isSuccess());

        AssetAvailabilityView view = viewHandler.handle(new GetAssetAvailabilityQuery("tenant-a", id))
                .await().indefinitely();
        assertTrue(view.available());
        assertEquals(T08, view.availableFrom());
    }

    @Test
    @DisplayName("an unavailability window overriding an availability window hides the asset")
    void unavailabilityOverridesAvailability() {
        UUID id = activeAsset("tenant-a", "AST-2");
        markHandler.handle(new MarkAssetAvailabilityCommand("tenant-a", id,
                AssetAvailabilityType.AVAILABLE, AssetAvailabilityReason.NORMAL_OPERATION,
                T08, T17, null, null)).await().indefinitely();
        markHandler.handle(new MarkAssetAvailabilityCommand("tenant-a", id,
                AssetAvailabilityType.UNAVAILABLE, AssetAvailabilityReason.MAINTENANCE,
                T08, T12, "WO-1", null)).await().indefinitely();

        AssetAvailabilityView view = viewHandler.handle(new GetAssetAvailabilityQuery("tenant-a", id))
                .await().indefinitely();
        assertFalse(view.available());
        assertEquals(T08, view.unavailableFrom());
        assertEquals(T12, view.unavailableUntil());
        assertEquals(AssetAvailabilityReason.MAINTENANCE, view.reason());
    }

    @Test
    @DisplayName("a same-type overlap is rejected as a conflict")
    void rejectsSameTypeOverlap() {
        UUID id = activeAsset("tenant-a", "AST-3");
        markHandler.handle(new MarkAssetAvailabilityCommand("tenant-a", id,
                AssetAvailabilityType.UNAVAILABLE, AssetAvailabilityReason.MAINTENANCE,
                T10, T12, null, null)).await().indefinitely();
        Result<AvailabilityResult> conflict = markHandler.handle(new MarkAssetAvailabilityCommand(
                "tenant-a", id, AssetAvailabilityType.UNAVAILABLE, AssetAvailabilityReason.REPAIR,
                T10, T17, null, null)).await().indefinitely();
        assertTrue(conflict.isFailure());
        ApplicationErrorException overlap = assertThrows(ApplicationErrorException.class, conflict::orElseThrow);
        assertEquals("availability.overlap", overlap.error().code());
    }

    @Test
    @DisplayName("a disposed asset cannot participate in availability")
    void rejectsDisposedAsset() {
        UUID id = activeAsset("tenant-a", "AST-4");
        new RetireAssetHandler(assets, new NoopEventPublisher(), new DirectUnitOfWork(), CLOCK)
                .handle(new RetireAssetCommand("tenant-a", AssetId.of(id))).await().indefinitely();
        new DisposeAssetHandler(assets, new NoopEventPublisher(), new DirectUnitOfWork(), CLOCK)
                .handle(new DisposeAssetCommand("tenant-a", AssetId.of(id))).await().indefinitely();
        Result<AvailabilityResult> rejected = markHandler.handle(new MarkAssetAvailabilityCommand(
                "tenant-a", id, AssetAvailabilityType.AVAILABLE, null, T08, T17, null, null))
                .await().indefinitely();
        assertTrue(rejected.isFailure());
        ApplicationErrorException disposed = assertThrows(ApplicationErrorException.class, rejected::orElseThrow);
        assertEquals("availability.asset-disposed", disposed.error().code());
    }

    @Test
    @DisplayName("keyed utilization ingestion is idempotent")
    void utilizationIdempotent() {
        UUID id = activeAsset("tenant-a", "AST-5");
        Result<UtilizationResult> first = utilizationHandler.handle(new RecordAssetUtilizationCommand(
                "tenant-a", id, T08, T10, AssetUtilizationType.TRIP, BigDecimal.TEN, "km",
                "SAP", "TRIP-1")).await().indefinitely();
        Result<UtilizationResult> second = utilizationHandler.handle(new RecordAssetUtilizationCommand(
                "tenant-a", id, T08, T10, AssetUtilizationType.TRIP, BigDecimal.TEN, "km",
                "SAP", "TRIP-1")).await().indefinitely();

        assertFalse(first.orElseThrow().duplicate());
        assertTrue(second.orElseThrow().duplicate());
        assertEquals(1, utilization.records.size());
        assertEquals(first.orElseThrow().utilizationId(), second.orElseThrow().utilizationId());
    }

    // ── Test doubles ─────────────────────────────────────────────────────────

    private static final class InMemoryAssetRepository implements AssetRepository {
        private final Map<UUID, Asset> store = new ConcurrentHashMap<>();

        @Override public CompletionStage<Asset> save(Asset aggregate) {
            store.put(aggregate.id().value(), aggregate);
            return CompletableFuture.completedFuture(aggregate);
        }

        @Override public CompletionStage<Optional<Asset>> findById(AssetId id) {
            return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id.value())));
        }

        @Override public CompletionStage<Boolean> existsById(AssetId id) {
            return CompletableFuture.completedFuture(store.containsKey(id.value()));
        }

        @Override public CompletionStage<Void> delete(Asset aggregate) {
            store.remove(aggregate.id().value());
            return CompletableFuture.completedFuture(null);
        }

        @Override public CompletionStage<Void> deleteById(AssetId id) {
            store.remove(id.value());
            return CompletableFuture.completedFuture(null);
        }

        @Override public CompletionStage<Boolean> existsByAssetNumber(String tenantId, String assetNumber) {
            return CompletableFuture.completedFuture(store.values().stream()
                    .anyMatch(a -> a.tenantId().equals(tenantId) && a.assetNumber().equals(assetNumber)));
        }

        @Override public CompletionStage<Optional<Asset>> findByTenantAndId(String tenantId, AssetId assetId) {
            Asset asset = store.get(assetId.value());
            boolean visible = asset != null && asset.tenantId().equals(tenantId);
            return CompletableFuture.completedFuture(visible ? Optional.of(asset) : Optional.empty());
        }

        @Override public CompletionStage<Boolean> existsByTenantAndId(String tenantId, AssetId assetId) {
            return findByTenantAndId(tenantId, assetId).thenApply(Optional::isPresent);
        }

        @Override public CompletionStage<Void> deleteByTenantAndId(String tenantId, AssetId assetId) {
            Asset asset = store.get(assetId.value());
            if (asset != null && asset.tenantId().equals(tenantId)) {
                store.remove(assetId.value());
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class FakeAvailabilityRepository implements AssetAvailabilityRepository {
        private final List<AssetAvailabilityPeriod> periods = new ArrayList<>();

        @Override public CompletionStage<AssetAvailabilityPeriod> save(
                String tenantId, AssetAvailabilityPeriod period) {
            periods.add(period);
            return CompletableFuture.completedFuture(period);
        }

        @Override public CompletionStage<Optional<AssetAvailabilityPeriod>> findById(
                String tenantId, AssetAvailabilityPeriodId id) {
            return CompletableFuture.completedFuture(periods.stream()
                    .filter(p -> p.id().equals(id)).findFirst());
        }

        @Override public CompletionStage<List<AssetAvailabilityPeriod>> findByAssetId(
                String tenantId, UUID assetId, Instant from, Instant to) {
            return CompletableFuture.completedFuture(periods.stream()
                    .filter(p -> p.tenantId().equals(tenantId) && p.assetId().equals(assetId))
                    .collect(Collectors.toList()));
        }

        @Override public CompletionStage<Boolean> hasOverlap(
                String tenantId, UUID assetId, AssetAvailabilityType type, Instant startsAt, Instant endsAt) {
            return CompletableFuture.completedFuture(periods.stream()
                    .filter(p -> p.tenantId().equals(tenantId) && p.assetId().equals(assetId)
                            && p.type() == type)
                    .anyMatch(p -> p.overlaps(startsAt, endsAt)));
        }

        @Override public CompletionStage<Optional<AssetAvailabilityPeriod>> findOpen(
                String tenantId, UUID assetId) {
            return CompletableFuture.completedFuture(periods.stream()
                    .filter(p -> p.tenantId().equals(tenantId) && p.assetId().equals(assetId) && p.isOpen())
                    .findFirst());
        }
    }

    private static final class FakeUtilizationRepository implements AssetUtilizationRepository {
        private final List<AssetUtilizationRecord> records = new ArrayList<>();

        @Override public CompletionStage<AssetUtilizationRecord> save(
                String tenantId, AssetUtilizationRecord record) {
            records.add(record);
            return CompletableFuture.completedFuture(record);
        }

        @Override public CompletionStage<Optional<AssetUtilizationRecord>> findBySourceRef(
                String tenantId, String source, String referenceId) {
            return CompletableFuture.completedFuture(records.stream()
                    .filter(r -> r.tenantId().equals(tenantId) && source.equals(r.source())
                            && referenceId.equals(r.referenceId()))
                    .findFirst());
        }

        @Override public CompletionStage<List<AssetUtilizationRecord>> findByAssetId(
                String tenantId, UUID assetId, Instant from, Instant to) {
            return CompletableFuture.completedFuture(records.stream()
                    .filter(r -> r.tenantId().equals(tenantId) && r.assetId().equals(assetId))
                    .collect(Collectors.toList()));
        }
    }

    private static final class NoopEventPublisher implements EventPublisher {
        @Override public Uni<Void> publish(List<DomainEvent> events) {
            return Uni.createFrom().nullItem();
        }
    }

    private static final class DirectUnitOfWork implements UnitOfWork {
        @Override public <R> Uni<R> execute(Supplier<Uni<R>> work) {
            return work.get();
        }
    }
}