package tech.kayys.numou.asset.infrastructure.maintenance;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.domain.maintenance.AssetReference;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.spi.asset.AssetReader;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Backs the Maintenance capability's {@link AssetReference} port with the
 * Asset context's own SPI reader (ASSET-19 §19.16).
 *
 * <p>This keeps Maintenance depending on a narrow reference port rather than on
 * the Asset aggregate, and centralises tenant isolation in the reader.</p>
 */
@ApplicationScoped
public class AssetReferenceAdapter implements AssetReference {

    private final AssetReader reader;

    public AssetReferenceAdapter(AssetReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    @Override
    public CompletionStage<Boolean> assetExists(String tenantId, UUID assetId) {
        return reader.exists(tenantId, AssetId.of(assetId));
    }
}