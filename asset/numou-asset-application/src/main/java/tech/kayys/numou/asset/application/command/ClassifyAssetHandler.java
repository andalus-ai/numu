package tech.kayys.numou.asset.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.classification.AssetClassification;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.UUID;

public class ClassifyAssetHandler extends AbstractAssetCommandHandler
        implements CommandHandler<ClassifyAssetCommand, Result<UUID>> {

    public ClassifyAssetHandler(AssetRepository repository, EventPublisher eventPublisher,
                                UnitOfWork unitOfWork, DomainClock clock) {
        super(repository, eventPublisher, unitOfWork, clock);
    }

    @Override
    public Uni<Result<UUID>> handle(ClassifyAssetCommand command) {
        return requireAsset(command.tenantId(), command.assetId())
                .flatMap(asset -> {
                    asset.classify(AssetClassification.of(command.classificationId(), command.classificationName()), clock());
                    return save(asset);
                })
                .map(saved -> Result.success(saved.id().value()));
    }
}
