package tech.kayys.numou.asset.application.inspection;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.inspection.AssetInspectionId;
import tech.kayys.numou.asset.domain.repository.AssetInspectionRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.asset.domain.repository.InspectionFindingRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

public class StartAssetInspectionHandler extends AbstractAssetInspectionHandler
        implements CommandHandler<StartAssetInspectionCommand, Result<AssetInspectionReadModel>> {

    public StartAssetInspectionHandler(AssetRepository a, AssetInspectionRepository i,
            InspectionFindingRepository f, EventPublisher p, UnitOfWork u, DomainClock c) {
        super(a, i, f, p, u, c);
    }

    @Override
    public Uni<Result<AssetInspectionReadModel>> handle(StartAssetInspectionCommand cmd) {
        return requireInspection(cmd.tenantId(), AssetInspectionId.of(cmd.inspectionId()))
                .flatMap(inspection -> {
                    inspection.start(clock);
                    return save(inspection);
                })
                .map(saved -> Result.success(AssetInspectionReadModel.from(saved)));
    }
}
