package tech.kayys.numou.construction.spi.hse;

import tech.kayys.numou.construction.domain.hse.SafetyProfile;
import tech.kayys.numou.construction.domain.hse.SafetyProfileId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface SafetyProfileRepository extends Repository<SafetyProfile, SafetyProfileId> {
    CompletionStage<Optional<SafetyProfile>> findByProjectIdAndSiteId(UUID projectId, UUID siteId);
}
