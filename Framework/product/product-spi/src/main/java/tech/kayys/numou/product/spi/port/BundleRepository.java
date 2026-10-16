package tech.kayys.syirkah.product.spi.port;

import tech.kayys.syirkah.foundation.domain.repository.Repository;
import tech.kayys.syirkah.product.domain.bundle.Bundle;
import tech.kayys.syirkah.product.domain.bundle.BundleId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for commercial bundles.
 *
 * Bundle codes are unique per tenant; the application layer enforces
 * uniqueness via {@link #existsByCode(String)} before creation.
 */
public interface BundleRepository
        extends Repository<Bundle, BundleId> {

    CompletionStage<Optional<Bundle>> findByCode(String code);

    CompletionStage<Boolean> existsByCode(String code);
}
