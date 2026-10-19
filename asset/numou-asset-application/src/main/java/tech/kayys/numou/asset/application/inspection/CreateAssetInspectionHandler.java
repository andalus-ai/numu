package tech.kayys.numou.asset.application.inspection;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.inspection.AssetInspection;
import tech.kayys.numou.asset.domain.inspection.AssetInspectionId;
import tech.kayys.numou.asset.domain.repository.AssetInspectionRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.asset.domain.repository.InspectionFindingRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.UUID;

public class CreateAssetInspectionHandler extends AbstractAssetInspectionHandler
        implements CommandHandler<CreateAssetInspectionCommand, Result<AssetInspectionReadModel>> {

    public CreateAssetInspectionHandler(AssetRepository a, AssetInspectionRepository i,
            InspectionFindingRepository f, EventPublisher p, UnitOfWork u, DomainClock c) {
        super(a, i, f, p, u, c);
    }

    @Override
    public Uni<Result<AssetInspectionReadModel>> handle(CreateAssetInspectionCommand cmd) {
        return requireAsset(cmd.tenantId(), cmd.assetId()).flatMap(v -> {
            AssetInspection inspection = AssetInspection.create(
                    AssetInspectionId.generate(), cmd.tenantId(), cmd.assetId(),
                    "INSP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                    cmd.type(), cmd.inspectorId(), cmd.notes(), cmd.workOrderId(),
                    cmd.scheduledFor(), clock);
            return save(inspection).map(saved -> Result.success(AssetInspectionReadModel.from(saved)));
        });
    }
}
