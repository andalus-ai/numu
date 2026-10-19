package tech.kayys.numou.asset.application.inspection;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.inspection.AssetInspectionId;
import tech.kayys.numou.asset.domain.inspection.finding.InspectionFinding;
import tech.kayys.numou.asset.domain.repository.AssetInspectionRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.asset.domain.repository.InspectionFindingRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

public class AddInspectionFindingHandler extends AbstractAssetInspectionHandler
        implements CommandHandler<AddInspectionFindingCommand, Result<InspectionFinding>> {

    public AddInspectionFindingHandler(AssetRepository a, AssetInspectionRepository i,
            InspectionFindingRepository f, EventPublisher p, UnitOfWork u, DomainClock c) {
        super(a, i, f, p, u, c);
    }

    @Override
    public Uni<Result<InspectionFinding>> handle(AddInspectionFindingCommand cmd) {
        if (cmd.description() == null || cmd.description().isBlank()) {
            return Uni.createFrom().item(Result.failure(
                    ApplicationError.of("asset.finding.description-required", "description is required")));
        }
        if (cmd.severity() == null) {
            return Uni.createFrom().item(Result.failure(
                    ApplicationError.of("asset.finding.severity-required", "severity is required")));
        }
        return requireInspection(cmd.tenantId(), AssetInspectionId.of(cmd.inspectionId()))
                .flatMap(inspection -> {
                    InspectionFinding finding = inspection.addFinding(cmd.category(), cmd.description(),
                            cmd.severity(), cmd.recommendedAction(), cmd.workOrderId(),
                            cmd.recordedBy(), clock);
                    return unitOfWork.execute(() -> Uni.createFrom()
                            .completionStage(() -> inspectionRepository.save(inspection.tenantId(), inspection))
                            .flatMap(s -> Uni.createFrom()
                                    .completionStage(() -> findingRepository.save(inspection.tenantId(), finding)))
                            .flatMap(s -> eventPublisher.publish(inspection.pullDomainEvents())
                                    .replaceWith(finding)))
                            .map(saved -> Result.success(saved));
                });
    }
}
