package tech.kayys.syirkah.product.spi.port;

import tech.kayys.syirkah.foundation.domain.repository.Repository;
import tech.kayys.syirkah.product.domain.classification.ClassificationScheme;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for classification schemes. Scheme codes are unique,
 * which the application layer enforces through {@link #existsByCode}
 * before creation.
 */
public interface ClassificationSchemeRepository
        extends Repository<ClassificationScheme, ClassificationSchemeId> {

    CompletionStage<Optional<ClassificationScheme>> findByCode(String code);

    CompletionStage<Boolean> existsByCode(String code);
}
