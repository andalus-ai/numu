package tech.kayys.syirkah.accounting.interfaces.repository;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.syirkah.accounting.application.port.FixedAssetRepository;
import tech.kayys.syirkah.accounting.domain.identifier.FixedAssetId;
import tech.kayys.syirkah.accounting.domain.model.FixedAsset;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class InMemoryFixedAssetRepository implements FixedAssetRepository {
    private final Map<FixedAssetId, FixedAsset> store = new ConcurrentHashMap<>();

    @Override
    public Uni<Optional<FixedAsset>> findById(FixedAssetId id) {
        return Uni.createFrom().item(Optional.ofNullable(store.get(id)));
    }

    @Override
    public Uni<List<FixedAsset>> findAll() {
        return Uni.createFrom().item(new ArrayList<>(store.values()));
    }

    @Override
    public Uni<Void> save(FixedAsset asset) {
        store.put(asset.id(), asset);
        return Uni.createFrom().voidItem();
    }
}
