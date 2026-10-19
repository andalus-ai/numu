package tech.kayys.numou.asset.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.model.Asset;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

public class CreateAssetHandler extends AbstractAssetCommandHandler
        implements CommandHandler<CreateAssetCommand, Result<CreateAssetResult>> {

    public CreateAssetHandler(AssetRepository repository, EventPublisher eventPublisher,
                              UnitOfWork unitOfWork, DomainClock clock) {
        super(repository, eventPublisher, unitOfWork, clock);
    }

    @Override
    public Uni<Result<CreateAssetResult>> handle(CreateAssetCommand command) {
        return Uni.createFrom()
                .completionStage(() -> repository.existsByAssetNumber(command.tenantId(), command.assetNumber()))
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Uni.createFrom().item(Result.<CreateAssetResult>failure(
                                ApplicationError.of("asset.number.duplicate",
                                        "Asset number already exists: " + command.assetNumber())));
                    }
                    Asset asset = Asset.create(AssetId.generate(), command.tenantId(),
                            command.assetNumber(), command.name(), command.type(), clock());
                    return save(asset).map(saved -> Result.success(CreateAssetResult.from(saved)));
                });
    }
}
