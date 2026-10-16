package tech.kayys.syirkah.accounting.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.identifier.FixedAssetId;
import tech.kayys.syirkah.accounting.domain.model.FixedAsset;

import java.util.List;
import java.util.Optional;

public interface FixedAssetRepository {
    Uni<Optional<FixedAsset>> findById(FixedAssetId id);
    Uni<List<FixedAsset>> findAll();
    Uni<Void> save(FixedAsset asset);
}
