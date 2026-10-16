package tech.kayys.numou.asset.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.location.AssetLocation;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.UUID;

public class ChangeAssetLocationHandler extends AbstractAssetCommandHandler
        implements CommandHandler<ChangeAssetLocationCommand, Result<UUID>> {

    public ChangeAssetLocationHandler(AssetRepository repository, EventPublisher eventPublisher,
                                      UnitOfWork unitOfWork, DomainClock clock) {
        super(repository, eventPublisher, unitOfWork, clock);
    }

    @Override
    public Uni<Result<UUID>> handle(ChangeAssetLocationCommand command) {
        return requireAsset(command.tenantId(), command.assetId())
                .flatMap(asset -> {
                    asset.changeLocation(AssetLocation.of(command.locationId(), command.locationName()), clock());
                    return save(asset);
                })
                .map(saved -> Result.success(saved.id().value()));
    }
}
