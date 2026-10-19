package tech.kayys.numou.asset.application.document;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.document.AssetDocumentReference;
import tech.kayys.numou.asset.domain.repository.AssetDocumentReferenceRepository;

import java.util.List;
import java.util.Objects;

/** Read-side handler: documents of one asset (ASSET-24). */
@jakarta.enterprise.context.ApplicationScoped
public class GetAssetDocumentsHandler {

    private final AssetDocumentReferenceRepository references;

    public GetAssetDocumentsHandler(AssetDocumentReferenceRepository references) {
        this.references = Objects.requireNonNull(references, "references");
    }

    public Uni<List<AssetDocumentReference>> handle(GetAssetDocumentsQuery query) {
        return Uni.createFrom()
                .completionStage(() -> references.findByAsset(query.tenantId(), query.assetId()));
    }
}
