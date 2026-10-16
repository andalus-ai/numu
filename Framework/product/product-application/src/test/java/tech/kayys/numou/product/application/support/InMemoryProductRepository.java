package tech.kayys.syirkah.product.application.support;

import tech.kayys.syirkah.product.domain.product.Product;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.spi.port.ProductRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Test double for {@link ProductRepository}. */
public final class InMemoryProductRepository
        implements ProductRepository {

    private final Map<ProductId, Product> productsById =
            new LinkedHashMap<>();

    @Override
    public CompletionStage<Product> save(Product product) {
        productsById.put(product.id(), product);

        return CompletableFuture.completedFuture(product);
    }

    @Override
    public CompletionStage<Optional<Product>> findById(ProductId id) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(productsById.get(id))
        );
    }

    @Override
    public CompletionStage<Optional<Product>> findByCode(String code) {
        return CompletableFuture.completedFuture(
                productsById.values().stream()
                        .filter(product -> product.code().equals(code))
                        .findFirst()
        );
    }

    @Override
    public CompletionStage<Boolean> existsByCode(String code) {
        return CompletableFuture.completedFuture(
                productsById.values().stream()
                        .anyMatch(product -> product.code().equals(code))
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(ProductId id) {
        return CompletableFuture.completedFuture(
                productsById.containsKey(id)
        );
    }

    @Override
    public CompletionStage<Void> delete(Product product) {
        productsById.remove(product.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ProductId id) {
        productsById.remove(id);

        return CompletableFuture.completedFuture(null);
    }
}