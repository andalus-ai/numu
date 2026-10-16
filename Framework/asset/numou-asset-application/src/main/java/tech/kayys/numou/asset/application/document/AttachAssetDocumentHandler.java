package tech.kayys.numou.asset.application.document;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.application.command.AbstractAssetCommandHandler;
import tech.kayys.numou.asset.domain.document.AssetDocumentReference;
import tech.kayys.numou.asset.domain.document.AssetDocumentReferenceId;
import tech.kayys.numou.asset.domain.document.DocumentReference;
import tech.kayys.numou.asset.domain.event.AssetDocumentAttached;
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

/**
 * Attaches an existing document to an asset (ASSET-24 §20).
 *
 * <p>Rules: asset must exist in the same tenant; document must exist via the
 * {@link DocumentReference} port (same tenant); duplicate
 * (asset, document) references are rejected; primary uniqueness per
 * (tenant, asset, type) is enforced here with the DB partial unique index as
 * the atomic backstop.</p>
 */
@jakarta.enterprise.context.ApplicationScoped
public class AttachAssetDocumentHandler extends AbstractAssetCommandHandler
        implements CommandHandler<AttachAssetDocumentCommand, Result<UUID>> {

    private final AssetDocumentReferenceRepository references;
    private final DocumentReference documents;

    public AttachAssetDocumentHandler(AssetRepository repository,
                                      AssetDocumentReferenceRepository references,
                                      DocumentReference documents,
                                      EventPublisher eventPublisher,
                                      UnitOfWork unitOfWork,
                                      DomainClock clock) {
        super(repository, eventPublisher, unitOfWork, clock);
        this.references = references;
        this.documents = documents;
    }

    @Override
    public Uni<Result<UUID>> handle(AttachAssetDocumentCommand command) {
        return requireAsset(command.tenantId(), command.assetId())
                .flatMap(asset -> Uni.createFrom()
                        .completionStage(() -> documents.exists(command.tenantId(), command.documentId()))
                        .flatMap(exists -> {
                            if (!Boolean.TRUE.equals(exists)) {
                                return Uni.createFrom().failure(new ApplicationErrorException(
                                        ApplicationError.of("asset.document.not-found",
                                                "Document not found: " + command.documentId())));
                            }
                            return Uni.createFrom().completionStage(
                                    () -> references.findByAsset(command.tenantId(), command.assetId()));
                        })
                        .flatMap(existing -> {
                            boolean duplicate = existing.stream()
                                    .anyMatch(r -> r.documentId().equals(command.documentId()));
                            if (duplicate) {
                                return Uni.createFrom().item(Result.<UUID>failure(ApplicationError.of(
                                        "asset.document.duplicate",
                                        "Document already attached: " + command.documentId())));
                            }
                            if (command.primary()) {
                                boolean primaryTaken = existing.stream()
                                        .anyMatch(r -> r.type() == command.type() && r.primary());
                                if (primaryTaken) {
                                    return Uni.createFrom().item(Result.<UUID>failure(ApplicationError.of(
                                            "asset.document.primary-conflict",
                                            "Primary document already set for type: " + command.type())));
                                }
                            }
                            AssetDocumentReference reference = new AssetDocumentReference(
                                    AssetDocumentReferenceId.generate(),
                                    command.tenantId(), command.assetId(), command.documentId(),
                                    command.documentVersion(), command.type(), command.title(),
                                    command.primary(), command.required(),
                                    command.validFrom(), command.validUntil(),
                                    clock().now(), command.linkedBy());
                            return unitOfWork.execute(() -> Uni.createFrom()
                                            .completionStage(() -> references.save(reference))
                                            .flatMap(saved -> eventPublisher.publish(List.of(
                                                    new AssetDocumentAttached(UUID.randomUUID(), clock().now(),
                                                            saved.assetId().value(), saved.documentId(),
                                                            saved.type()))).replaceWith(saved)))
                                    .map(saved -> Result.success(saved.id().value()));
                        }));
    }
}
