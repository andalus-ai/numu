package tech.kayys.syirkah.construction.spi.profile;

import tech.kayys.syirkah.construction.domain.profile.ConstructionProjectProfile;
import tech.kayys.syirkah.construction.domain.profile.ConstructionProjectProfileId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ConstructionProjectProfileRepository
        extends Repository<ConstructionProjectProfile, ConstructionProjectProfileId> {
    CompletionStage<Optional<ConstructionProjectProfile>> findByProjectId(UUID projectId);
}
