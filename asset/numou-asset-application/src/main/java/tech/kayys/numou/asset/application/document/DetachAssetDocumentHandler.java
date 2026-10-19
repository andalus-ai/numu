package tech.kayys.numou.asset.application.document;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.application.command.AbstractAssetCommandHandler;
import tech.kayys.numou.asset.domain.event.AssetDocumentDetached;
import tech.kayys.numou.asset.domain.repository.AssetDocumentReferenceRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.List;
import java.util.UUID;

/** Detaches a document reference; tenant-scoped (ASSET-24 §20). */
@jakarta.enterprise.context.ApplicationScoped
public class DetachAssetDocumentHandler extends AbstractAssetCommandHandler
        implements CommandHandler<DetachAssetDocumentCommand, Result<Void>> {

    private final AssetDocumentReferenceRepository references;

    public DetachAssetDocumentHandler(AssetRepository repository,
                                      AssetDocumentReferenceRepository references,
                                      EventPublisher eventPublisher,
                                      UnitOfWork unitOfWork,
                                      DomainClock clock) {
        super(repository, eventPublisher, unitOfWork, clock);
        this.references = references;
    }

    @Override
    public Uni<Result<Void>> handle(DetachAssetDocumentCommand command) {
        return Uni.createFrom()
                .completionStage(() -> references.findById(command.tenantId(), command.referenceId()))
                .flatMap(opt -> {
                    var reference = opt.orElse(null);
                    if (reference == null) {
                        return Uni.createFrom().failure(new ApplicationErrorException(
                                ApplicationError.of("asset.document.not-found",
                                        "Document reference not found")));
                    }
                    return unitOfWork.execute(() -> Uni.createFrom()
                                    .completionStage(
                                            () -> references.delete(command.tenantId(), command.referenceId()))
                                    .flatMap(ignored -> eventPublisher.publish(List.of(
                                            new AssetDocumentDetached(UUID.randomUUID(), clock().now(),
                                                    reference.assetId().value(), reference.documentId())))))
                            .map(ignored -> Result.<Void>success(null));
                });
    }
}
