package tech.kayys.numou.construction.spi.warranty;

import tech.kayys.numou.construction.domain.warranty.Warranty;
import tech.kayys.numou.construction.domain.warranty.WarrantyId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface WarrantyRepository extends Repository<Warranty, WarrantyId> {
    CompletionStage<Optional<Warranty>> findByProjectId(UUID projectId);
}
