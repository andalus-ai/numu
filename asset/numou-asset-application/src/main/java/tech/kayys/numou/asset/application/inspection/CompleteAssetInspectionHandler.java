package tech.kayys.numou.asset.application.inspection;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.inspection.AssetInspectionId;
import tech.kayys.numou.asset.domain.repository.AssetInspectionRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.asset.domain.repository.InspectionFindingRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

public class CompleteAssetInspectionHandler extends AbstractAssetInspectionHandler
        implements CommandHandler<CompleteAssetInspectionCommand, Result<AssetInspectionReadModel>> {

    public CompleteAssetInspectionHandler(AssetRepository a, AssetInspectionRepository i,
            InspectionFindingRepository f, EventPublisher p, UnitOfWork u, DomainClock c) {
        super(a, i, f, p, u, c);
    }

    @Override
    public Uni<Result<AssetInspectionReadModel>> handle(CompleteAssetInspectionCommand cmd) {
        if (cmd.condition() == null) {
            return Uni.createFrom().item(Result.failure(
                    ApplicationError.of("asset.inspection.condition-required", "condition is required")));
        }
        if (cmd.result() == null) {
            return Uni.createFrom().item(Result.failure(
                    ApplicationError.of("asset.inspection.result-required", "result is required")));
        }
        return requireInspection(cmd.tenantId(), AssetInspectionId.of(cmd.inspectionId()))
                .flatMap(inspection -> {
                    inspection.complete(cmd.condition(), cmd.result(), clock);
                    return save(inspection);
                })
                .map(saved -> Result.success(AssetInspectionReadModel.from(saved)));
    }
}
