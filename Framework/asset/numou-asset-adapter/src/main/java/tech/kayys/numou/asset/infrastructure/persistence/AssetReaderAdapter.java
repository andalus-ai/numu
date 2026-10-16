package tech.kayys.numou.asset.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.asset.spi.asset.AssetReader;
import tech.kayys.numou.asset.spi.asset.AssetSummary;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Inbound adapter for other bounded contexts (FMS, Project, Accounting) to
 * verify/read asset facts through the SPI (see ASSET-10 / ASSET-14).
 *
 * <p>Tenant-scoped: a caller cannot probe another tenant's asset.</p>
 */
@ApplicationScoped
public class AssetReaderAdapter implements AssetReader {

    private final AssetRepository repository;

    public AssetReaderAdapter(AssetRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public CompletionStage<Boolean> exists(String tenantId, AssetId assetId) {
        return repository.existsByTenantAndId(tenantId, assetId);
    }

    @Override
    public CompletionStage<Optional<AssetSummary>> findSummary(String tenantId, AssetId assetId) {
        return repository.findByTenantAndId(tenantId, assetId)
                .thenApply(optional -> optional.map(asset -> new AssetSummary(
                        asset.id(),
                        asset.tenantId(),
                        asset.assetNumber(),
                        asset.name(),
                        asset.type(),
                        asset.status())));
    }
}
