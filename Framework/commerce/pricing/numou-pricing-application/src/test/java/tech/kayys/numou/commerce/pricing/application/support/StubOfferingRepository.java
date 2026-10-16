package tech.kayys.syirkah.commerce.pricing.application.support;

import tech.kayys.syirkah.commerce.offering.domain.ChannelId;
import tech.kayys.syirkah.commerce.offering.domain.OfferingStatus;
import tech.kayys.syirkah.commerce.offering.domain.OfferingType;
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

/**
 * Test double for offerings: supports ACTIVE vs DRAFT setups.
 */
public final class StubOfferingRepository implements ProductOfferingRepository {

    private final Map<ProductOfferingId, ProductOffering> offeringsById = new LinkedHashMap<>();

    public ProductOffering activeOffering(ProductId productId) {
        var offering = ProductOffering.create(
                ProductOfferingId.generate(),
                productId,
                "Test offering",
                OfferingType.STANDARD,
                ChannelId.of("POS"),
                null,
                null);
        offering.activate();
        offeringsById.put(offering.id(), offering);
        return offering;
    }

    public ProductOffering draftOffering(ProductId productId) {
        var offering = ProductOffering.create(
                ProductOfferingId.generate(),
                productId,
                "Draft offering",
                OfferingType.STANDARD,
                ChannelId.of("POS"),
                null,
                null);
        offeringsById.put(offering.id(), offering);
        return offering;
    }

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
        return CompletableFuture.completedFuture(offeringsById.values().stream()
                .filter(o -> o.productId().equals(productId))
                .toList());
    }

    @Override
    public CompletionStage<List<ProductOffering>> findByChannelId(ChannelId channelId) {
        return CompletableFuture.completedFuture(offeringsById.values().stream()
                .filter(o -> o.channelId().equals(channelId))
                .toList());
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

    public OfferingStatus statusOf(ProductOfferingId id) {
        return offeringsById.get(id).status();
    }
}
