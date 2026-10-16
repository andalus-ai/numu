package tech.kayys.syirkah.commerce.offering.adapter.memory;

import tech.kayys.syirkah.commerce.offering.domain.ChannelId;
import tech.kayys.syirkah.commerce.offering.domain.ProductOffering;
import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.commerce.offering.spi.port.ProductOfferingRepository;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryProductOfferingRepository implements ProductOfferingRepository {

    private final Map<ProductOfferingId, ProductOffering> offeringsById = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<ProductOffering> save(ProductOffering offering) {
        Objects.requireNonNull(offering, "offering cannot be null");
        offeringsById.put(offering.id(), offering);
        return CompletableFuture.completedFuture(offering);
    }

    @Override
    public CompletionStage<Optional<ProductOffering>> findById(ProductOfferingId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(offeringsById.get(id)));
    }

    @Override
    public CompletionStage<List<ProductOffering>> findByProductId(ProductId productId) {
        Objects.requireNonNull(productId, "productId cannot be null");
        var list = offeringsById.values().stream()
                .filter(o -> o.productId().equals(productId))
                .toList();
        return CompletableFuture.completedFuture(list);
    }

    @Override
    public CompletionStage<List<ProductOffering>> findByChannelId(ChannelId channelId) {
        Objects.requireNonNull(channelId, "channelId cannot be null");
        var list = offeringsById.values().stream()
                .filter(o -> o.channelId().equals(channelId))
                .toList();
        return CompletableFuture.completedFuture(list);
    }

    @Override
    public CompletionStage<Boolean> existsById(ProductOfferingId id) {
        return CompletableFuture.completedFuture(offeringsById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(ProductOffering offering) {
        Objects.requireNonNull(offering, "offering cannot be null");
        offeringsById.remove(offering.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ProductOfferingId id) {
        offeringsById.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
