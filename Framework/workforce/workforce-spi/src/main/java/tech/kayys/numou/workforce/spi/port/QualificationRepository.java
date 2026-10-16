package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.qualification.Qualification;
import tech.kayys.numou.workforce.domain.qualification.QualificationId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for the {@link Qualification} aggregate root.
 */
public interface QualificationRepository extends Repository<Qualification, QualificationId> {

    /**
     * Looks up a qualification by its unique code.
     */
    CompletionStage<Optional<Qualification>> findByCode(String code);
}
