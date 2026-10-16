package tech.kayys.numou.construction.spi.profile;

import tech.kayys.numou.construction.domain.profile.ConstructionProjectProfile;
import tech.kayys.numou.construction.domain.profile.ConstructionProjectProfileId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ConstructionProjectProfileRepository
        extends Repository<ConstructionProjectProfile, ConstructionProjectProfileId> {
    CompletionStage<Optional<ConstructionProjectProfile>> findByProjectId(UUID projectId);
}
