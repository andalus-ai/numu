package tech.kayys.syirkah.commerce.offering.application.support;

import tech.kayys.syirkah.commerce.offering.domain.ChannelId;
import tech.kayys.syirkah.commerce.offering.domain.ProductOffering;
import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.commerce.offering.spi.port.ProductOfferingRepository;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class InMemoryProductOfferingRepository implements ProductOfferingRepository {

    private final Map<ProductOfferingId, ProductOffering> offeringsById = new LinkedHashMap<>();

    @Override
    public CompletionStage<ProductOffering> save(ProductOffering offering) {
        offeringsById.put(offering.id(), offering);
        return CompletableFuture.completedFuture(offering);
    }

    @Override
    public CompletionStage<Optional<ProductOffering>> findById(ProductOfferingId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(offeringsById.get(id)));
    }

    @Override
    public CompletionStage<List<ProductOffering>> findByProductId(ProductId productId) {
        var list = offeringsById.values().stream()
                .filter(o -> o.productId().equals(productId))
                .toList();
        return CompletableFuture.completedFuture(list);
    }

    @Override
    public CompletionStage<List<ProductOffering>> findByChannelId(ChannelId channelId) {
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
        offeringsById.remove(offering.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ProductOfferingId id) {
        offeringsById.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
