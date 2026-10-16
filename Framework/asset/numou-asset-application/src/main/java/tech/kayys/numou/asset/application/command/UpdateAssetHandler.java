package tech.kayys.numou.asset.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.UUID;

public class UpdateAssetHandler extends AbstractAssetCommandHandler
        implements CommandHandler<UpdateAssetCommand, Result<UUID>> {

    public UpdateAssetHandler(AssetRepository repository, EventPublisher eventPublisher,
                              UnitOfWork unitOfWork, DomainClock clock) {
        super(repository, eventPublisher, unitOfWork, clock);
    }

    @Override
    public Uni<Result<UUID>> handle(UpdateAssetCommand command) {
        return requireAsset(command.tenantId(), command.assetId())
                .flatMap(asset -> {
                    if (command.name() != null) {
                        asset.rename(command.name(), clock());
                    }
                    if (command.type() != null) {
                        asset.changeType(command.type(), clock());
                    }
                    return save(asset);
                })
                .map(saved -> Result.success(saved.id().value()));
    }
}
