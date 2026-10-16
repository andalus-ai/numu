package tech.kayys.numou.asset.spi.port;

import tech.kayys.numou.asset.domain.fixedasset.CipId;
import tech.kayys.numou.asset.domain.fixedasset.ConstructionProject;
import tech.kayys.numou.asset.domain.fixedasset.FixedAssetAggregate;
import tech.kayys.numou.asset.domain.fixedasset.LeaseContract;
import tech.kayys.numou.asset.domain.fixedasset.LeaseId;
import tech.kayys.numou.asset.domain.identifier.AssetId;

import java.util.Optional;

/** Persistence port for fixed assets, leases, and construction-in-progress projects. */
public interface FixedAssetStorePort {
    Optional<FixedAssetAggregate> findAsset(AssetId id);

    void saveAsset(FixedAssetAggregate asset);

    Optional<LeaseContract> findLease(LeaseId id);

    void saveLease(LeaseContract lease);

    Optional<ConstructionProject> findConstructionProject(CipId id);

    void saveConstructionProject(ConstructionProject project);
}
