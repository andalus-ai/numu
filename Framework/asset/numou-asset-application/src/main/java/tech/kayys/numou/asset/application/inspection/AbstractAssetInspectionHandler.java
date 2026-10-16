package tech.kayys.numou.asset.application.inspection;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.inspection.AssetInspection;
import tech.kayys.numou.asset.domain.inspection.AssetInspectionId;
import tech.kayys.numou.asset.domain.repository.AssetInspectionRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.asset.domain.repository.InspectionFindingRepository;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.List;
import java.util.Objects;

/** Shared orchestration helpers for inspection write use cases (ASSET-20). */
public abstract class AbstractAssetInspectionHandler {

    protected final AssetRepository assetRepository;
    protected final AssetInspectionRepository inspectionRepository;
    protected final InspectionFindingRepository findingRepository;
    protected final EventPublisher eventPublisher;
    protected final UnitOfWork unitOfWork;
    protected final DomainClock clock;

    protected AbstractAssetInspectionHandler(
            AssetRepository assetRepository,
            AssetInspectionRepository inspectionRepository,
            InspectionFindingRepository findingRepository,
            EventPublisher eventPublisher,
            UnitOfWork unitOfWork,
            DomainClock clock) {
        this.assetRepository = Objects.requireNonNull(assetRepository, "assetRepository");
        this.inspectionRepository = Objects.requireNonNull(inspectionRepository, "inspectionRepository");
        this.findingRepository = Objects.requireNonNull(findingRepository, "findingRepository");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    protected Uni<Void> requireAsset(String tenantId, java.util.UUID assetId) {
        return Uni.createFrom()
                .completionStage(() -> assetRepository.findByTenantAndId(tenantId, AssetId.of(assetId)))
                .map(opt -> opt.orElse(null))
                .onItem().ifNull().failWith(() -> new ApplicationErrorException(
                        ApplicationError.of("asset.not-found", "Asset not found: " + assetId)))
                .replaceWithVoid();
    }

    protected Uni<AssetInspection> requireInspection(String tenantId, AssetInspectionId id) {
        return Uni.createFrom()
                .completionStage(() -> inspectionRepository.findById(tenantId, id))
                .map(opt -> opt.orElse(null))
                .onItem().ifNull().failWith(() -> new ApplicationErrorException(
                        ApplicationError.of("asset.inspection-not-found", "Inspection not found: " + id.value())));
    }

    protected Uni<AssetInspection> save(AssetInspection inspection) {
        return unitOfWork.execute(() -> Uni.createFrom()
                .completionStage(() -> inspectionRepository.save(inspection.tenantId(), inspection))
                .flatMap(saved -> publishPendingEvents(saved).replaceWith(saved)));
    }

    private Uni<Void> publishPendingEvents(AssetInspection inspection) {
        List<DomainEvent> events = inspection.pullDomainEvents();
        if (events.isEmpty()) {
            return Uni.createFrom().nullItem();
        }
        return eventPublisher.publish(events);
    }
}
