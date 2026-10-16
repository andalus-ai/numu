package tech.kayys.syirkah.commerce.pricing.spi.port;

import tech.kayys.syirkah.commerce.pricing.domain.price.PriceList;
import tech.kayys.syirkah.commerce.pricing.domain.price.PriceListId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Persistence port for price-list definitions (product02.md). */
public interface PriceListRepository extends Repository<PriceList, PriceListId> {

    CompletionStage<Optional<PriceList>> findByCode(String code);

    CompletionStage<Boolean> existsByCode(String code);
}
