package tech.kayys.numou.asset.infrastructure.persistence.memory;

import tech.kayys.numou.asset.domain.fixedasset.CipId;
import tech.kayys.numou.asset.domain.fixedasset.ConstructionProject;
import tech.kayys.numou.asset.domain.fixedasset.FixedAssetAggregate;
import tech.kayys.numou.asset.domain.fixedasset.LeaseContract;
import tech.kayys.numou.asset.domain.fixedasset.LeaseId;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.spi.port.FixedAssetStorePort;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory adapter for fixed-asset, lease, and CIP aggregates. */
@ApplicationScoped
public class InMemoryFixedAssetStore implements FixedAssetStorePort {
    private final ConcurrentHashMap<AssetId, FixedAssetAggregate> assets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<LeaseId, LeaseContract> leases = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CipId, ConstructionProject> projects = new ConcurrentHashMap<>();

    @Override
    public Optional<FixedAssetAggregate> findAsset(AssetId id) {
        return Optional.ofNullable(assets.get(id));
    }

    @Override
    public void saveAsset(FixedAssetAggregate asset) {
        assets.put(asset.id(), asset);
    }

    @Override
    public Optional<LeaseContract> findLease(LeaseId id) {
        return Optional.ofNullable(leases.get(id));
    }

    @Override
    public void saveLease(LeaseContract lease) {
        leases.put(lease.id(), lease);
    }

    @Override
    public Optional<ConstructionProject> findConstructionProject(CipId id) {
        return Optional.ofNullable(projects.get(id));
    }

    @Override
    public void saveConstructionProject(ConstructionProject project) {
        projects.put(project.id(), project);
    }
}
