package tech.kayys.numou.education.school.spi;

import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.school.domain.School;
import tech.kayys.numou.foundation.domain.repository.Repository;

public interface SchoolRepository extends Repository<School, SchoolId> {}
