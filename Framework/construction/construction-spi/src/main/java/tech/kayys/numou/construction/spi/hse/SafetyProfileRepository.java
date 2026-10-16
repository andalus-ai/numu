package tech.kayys.syirkah.construction.spi.hse;

import tech.kayys.syirkah.construction.domain.hse.SafetyProfile;
import tech.kayys.syirkah.construction.domain.hse.SafetyProfileId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface SafetyProfileRepository extends Repository<SafetyProfile, SafetyProfileId> {
    CompletionStage<Optional<SafetyProfile>> findByProjectIdAndSiteId(UUID projectId, UUID siteId);
}
