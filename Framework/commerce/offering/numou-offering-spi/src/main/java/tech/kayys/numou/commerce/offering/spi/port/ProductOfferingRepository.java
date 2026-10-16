package tech.kayys.numou.commerce.offering.spi.port;

import tech.kayys.numou.commerce.offering.domain.ChannelId;
import tech.kayys.numou.commerce.offering.domain.ProductOffering;
import tech.kayys.numou.commerce.offering.domain.ProductOfferingId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.product.domain.product.ProductId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface ProductOfferingRepository extends Repository<ProductOffering, ProductOfferingId> {

    CompletionStage<List<ProductOffering>> findByProductId(ProductId productId);

    CompletionStage<List<ProductOffering>> findByChannelId(ChannelId channelId);
}
