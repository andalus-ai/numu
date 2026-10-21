package tech.kayys.numou.education.madrasah.spi;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.madrasah.domain.MadrasahProfile;
import tech.kayys.numou.education.madrasah.domain.MadrasahProfileId;
import tech.kayys.numou.foundation.domain.repository.Repository;

public interface MadrasahProfileRepository extends Repository<MadrasahProfile, MadrasahProfileId> {
    CompletionStage<Optional<MadrasahProfile>> findBySchool(SchoolId schoolId);
}
