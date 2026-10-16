package tech.kayys.syirkah.commerce.configuration.adapter.memory;

import tech.kayys.syirkah.commerce.configuration.domain.ProductConfiguration;
import tech.kayys.syirkah.commerce.configuration.domain.ProductConfigurationId;
import tech.kayys.syirkah.commerce.configuration.spi.port.ProductConfigurationRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory configuration store for tests and single-process demos. */
public final class InMemoryProductConfigurationRepository
        implements ProductConfigurationRepository {

    private final Map<ProductConfigurationId, ProductConfiguration> store =
            new ConcurrentHashMap<>();

    @Override
    public CompletionStage<ProductConfiguration> save(ProductConfiguration configuration) {
        store.put(configuration.id(), configuration);
        return CompletableFuture.completedFuture(configuration);
    }

    @Override
    public CompletionStage<Optional<ProductConfiguration>> findById(
            ProductConfigurationId id
    ) {
        return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
    }
}
