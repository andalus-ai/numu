package tech.kayys.syirkah.product.adapter.memory;

import tech.kayys.syirkah.product.domain.bundle.Bundle;
import tech.kayys.syirkah.product.domain.bundle.BundleId;
import tech.kayys.syirkah.product.spi.port.BundleRepository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link BundleRepository} adapter. */
public final class InMemoryBundleRepository implements BundleRepository {

    private final Map<BundleId, Bundle> bundlesById = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<Bundle> save(Bundle bundle) {
        Objects.requireNonNull(bundle, "bundle cannot be null");
        bundlesById.put(bundle.id(), bundle);
        return CompletableFuture.completedFuture(bundle);
    }

    @Override
    public CompletionStage<Optional<Bundle>> findById(BundleId id) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(bundlesById.get(id)));
    }

    @Override
    public CompletionStage<Optional<Bundle>> findByCode(String code) {
        Objects.requireNonNull(code, "code cannot be null");
        return CompletableFuture.completedFuture(
                bundlesById.values().stream()
                        .filter(bundle -> bundle.code().equals(code))
                        .findFirst());
    }

    @Override
    public CompletionStage<Boolean> existsByCode(String code) {
        Objects.requireNonNull(code, "code cannot be null");
        return CompletableFuture.completedFuture(
                bundlesById.values().stream()
                        .anyMatch(bundle -> bundle.code().equals(code)));
    }

    @Override
    public CompletionStage<Boolean> existsById(BundleId id) {
        return CompletableFuture.completedFuture(bundlesById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(Bundle bundle) {
        Objects.requireNonNull(bundle, "bundle cannot be null");
        bundlesById.remove(bundle.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(BundleId id) {
        bundlesById.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
