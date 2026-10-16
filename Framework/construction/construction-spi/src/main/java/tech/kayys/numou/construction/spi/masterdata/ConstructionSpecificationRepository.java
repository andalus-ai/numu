package tech.kayys.numou.construction.spi.masterdata;

import tech.kayys.numou.construction.domain.masterdata.ConstructionSpecification;
import tech.kayys.numou.construction.domain.masterdata.ConstructionSpecificationId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface ConstructionSpecificationRepository extends Repository<ConstructionSpecification, ConstructionSpecificationId> {
    CompletionStage<Optional<ConstructionSpecification>> findBySpecCode(String specCode);
}
