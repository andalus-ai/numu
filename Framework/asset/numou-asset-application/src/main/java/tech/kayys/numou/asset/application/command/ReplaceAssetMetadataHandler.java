package tech.kayys.numou.asset.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.repository.AssetMetadataRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;

/**
 * Validates the target asset, then replaces its dynamic attributes (ASSET-15).
 *
 * <p>The asset must exist <em>for the caller's tenant</em> before metadata is
 * written, so metadata can never be attached cross-tenant.</p>
 */
public class ReplaceAssetMetadataHandler {

    private final AssetRepository assets;
    private final AssetMetadataRepository metadata;

    public ReplaceAssetMetadataHandler(AssetRepository assets, AssetMetadataRepository metadata) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    public Uni<Result<AssetId>> handle(ReplaceAssetMetadataCommand command) {
        return Uni.createFrom()
                .completionStage(() -> assets.findByTenantAndId(command.tenantId(), command.assetId()))
                .map(optional -> optional.orElse(null))
                .onItem().ifNull().failWith(() -> new ApplicationErrorException(
                        ApplicationError.of("asset.not-found",
                                "Asset not found: " + command.assetId().value())))
                .flatMap(asset -> Uni.createFrom()
                        .completionStage(() -> metadata.replaceAttributes(
                                command.tenantId(), command.assetId(), command.attributes()))
                        .replaceWith(asset.id()))
                .map(Result::success);
    }
}
