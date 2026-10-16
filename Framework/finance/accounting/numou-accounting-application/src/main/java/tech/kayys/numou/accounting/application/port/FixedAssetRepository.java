package tech.kayys.numou.accounting.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.accounting.domain.identifier.FixedAssetId;
import tech.kayys.numou.accounting.domain.model.FixedAsset;

import java.util.List;
import java.util.Optional;

public interface FixedAssetRepository {
    Uni<Optional<FixedAsset>> findById(FixedAssetId id);
    Uni<List<FixedAsset>> findAll();
    Uni<Void> save(FixedAsset asset);
}
