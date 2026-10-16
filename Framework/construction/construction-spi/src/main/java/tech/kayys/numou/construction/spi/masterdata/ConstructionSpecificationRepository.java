package tech.kayys.syirkah.construction.spi.masterdata;

import tech.kayys.syirkah.construction.domain.masterdata.ConstructionSpecification;
import tech.kayys.syirkah.construction.domain.masterdata.ConstructionSpecificationId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface ConstructionSpecificationRepository extends Repository<ConstructionSpecification, ConstructionSpecificationId> {
    CompletionStage<Optional<ConstructionSpecification>> findBySpecCode(String specCode);
}
