package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.document.AssetDocumentReference;
import tech.kayys.syirkah.asset.domain.document.AssetDocumentReferenceId;
import tech.kayys.syirkah.asset.domain.identifier.AssetId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Port for asset document references (ASSET-24 §18). */
public interface AssetDocumentReferenceRepository {

    CompletionStage<AssetDocumentReference> save(AssetDocumentReference reference);

    CompletionStage<Optional<AssetDocumentReference>> findById(String tenantId, AssetDocumentReferenceId id);

    CompletionStage<List<AssetDocumentReference>> findByAsset(String tenantId, AssetId assetId);

    CompletionStage<List<AssetDocumentReference>> findExpiring(String tenantId, Instant now, Duration window);

    CompletionStage<Void> delete(String tenantId, AssetDocumentReferenceId id);
}
