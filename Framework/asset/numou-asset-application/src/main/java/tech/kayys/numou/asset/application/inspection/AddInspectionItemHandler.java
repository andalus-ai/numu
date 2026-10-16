package tech.kayys.numou.asset.application.inspection;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.inspection.AssetInspectionId;
import tech.kayys.numou.asset.domain.inspection.InspectionItem;
import tech.kayys.numou.asset.domain.repository.AssetInspectionRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.asset.domain.repository.InspectionFindingRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

public class AddInspectionItemHandler extends AbstractAssetInspectionHandler
        implements CommandHandler<AddInspectionItemCommand, Result<InspectionItem>> {

    public AddInspectionItemHandler(AssetRepository a, AssetInspectionRepository i,
            InspectionFindingRepository f, EventPublisher p, UnitOfWork u, DomainClock c) {
        super(a, i, f, p, u, c);
    }

    @Override
    public Uni<Result<InspectionItem>> handle(AddInspectionItemCommand cmd) {
        return requireInspection(cmd.tenantId(), AssetInspectionId.of(cmd.inspectionId()))
                .flatMap(inspection -> {
                    InspectionItem item = inspection.addItem(cmd.component(), cmd.description(),
                            cmd.condition(), cmd.result(), cmd.notes());
                    return save(inspection).map(saved -> Result.success(item));
                });
    }
}
