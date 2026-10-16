package tech.kayys.syirkah.commerce.offering.spi.port;

import tech.kayys.syirkah.commerce.offering.domain.ChannelId;
import tech.kayys.syirkah.commerce.offering.domain.ProductOffering;
import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface ProductOfferingRepository extends Repository<ProductOffering, ProductOfferingId> {

    CompletionStage<List<ProductOffering>> findByProductId(ProductId productId);

    CompletionStage<List<ProductOffering>> findByChannelId(ChannelId channelId);
}
