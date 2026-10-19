package tech.kayys.numou.asset.application.document;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.application.command.AbstractAssetCommandHandler;
import tech.kayys.numou.asset.domain.document.AssetDocumentReference;
import tech.kayys.numou.asset.domain.event.AssetDocumentPrimarySet;
import tech.kayys.numou.asset.domain.repository.AssetDocumentReferenceRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Designates the primary document of its type (ASSET-24 §21): demotes any
 * other primary of the same (tenant, asset, type), then promotes the target.
 */
@jakarta.enterprise.context.ApplicationScoped
public class SetPrimaryAssetDocumentHandler extends AbstractAssetCommandHandler
        implements CommandHandler<SetPrimaryAssetDocumentCommand, Result<Void>> {

    private final AssetDocumentReferenceRepository references;

    public SetPrimaryAssetDocumentHandler(AssetRepository repository,
                                          AssetDocumentReferenceRepository references,
                                          EventPublisher eventPublisher,
                                          UnitOfWork unitOfWork,
                                          DomainClock clock) {
        super(repository, eventPublisher, unitOfWork, clock);
        this.references = references;
    }

    @Override
    public Uni<Result<Void>> handle(SetPrimaryAssetDocumentCommand command) {
        return Uni.createFrom()
                .completionStage(() -> references.findById(command.tenantId(), command.referenceId()))
                .flatMap(opt -> {
                    AssetDocumentReference target = opt.orElse(null);
                    if (target == null) {
                        return Uni.createFrom().failure(new ApplicationErrorException(
                                ApplicationError.of("asset.document.not-found",
                                        "Document reference not found")));
                    }
                    return Uni.createFrom()
                            .completionStage(() -> references.findByAsset(command.tenantId(), target.assetId()))
                            .flatMap(all -> {
                                List<Uni<Void>> demotions = new ArrayList<>();
                                for (AssetDocumentReference other : all) {
                                    if (!other.id().equals(target.id())
                                            && other.type() == target.type()
                                            && other.primary()) {
                                        AssetDocumentReference demoted = new AssetDocumentReference(
                                                other.id(), other.tenantId(), other.assetId(),
                                                other.documentId(), other.documentVersion(), other.type(),
                                                other.title(), false, other.required(), other.validFrom(),
                                                other.validUntil(), other.linkedAt(), other.linkedBy());
                                        demotions.add(Uni.createFrom()
                                                .completionStage(() -> references.save(demoted)).replaceWithVoid());
                                    }
                                }
                                Uni<Void> demoteAll = demotions.isEmpty()
                                        ? Uni.createFrom().voidItem()
                                        : Uni.join().all(demotions).andCollectFailures()
                                                .replaceWithVoid();
                                AssetDocumentReference promoted = new AssetDocumentReference(
                                        target.id(), target.tenantId(), target.assetId(),
                                        target.documentId(), target.documentVersion(), target.type(),
                                        target.title(), true, target.required(), target.validFrom(),
                                        target.validUntil(), target.linkedAt(), target.linkedBy());
                                return unitOfWork.execute(() -> demoteAll.flatMap(ignored -> Uni.createFrom()
                                                .completionStage(() -> references.save(promoted))
                                                .flatMap(saved -> eventPublisher.publish(List.of(
                                                        new AssetDocumentPrimarySet(UUID.randomUUID(),
                                                                clock().now(), saved.assetId().value(),
                                                                saved.documentId(), saved.type()))))))
                                        .map(ignored -> Result.<Void>success(null));
                            });
                });
    }
}
