package tech.kayys.numou.asset.interfaces.producer;

import tech.kayys.numou.asset.application.fixedasset.FixedAssetService;
import tech.kayys.numou.asset.infrastructure.persistence.memory.InMemoryFixedAssetStore;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class FixedAssetServiceProducer {
    @Produces
    @ApplicationScoped
    public FixedAssetService fixedAssetService(InMemoryFixedAssetStore store) {
        return new FixedAssetService(store);
    }
}
