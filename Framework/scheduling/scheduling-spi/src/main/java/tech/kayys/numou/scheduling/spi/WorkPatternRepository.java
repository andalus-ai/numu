package tech.kayys.numou.scheduling.spi;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.scheduling.domain.WorkPattern;
import tech.kayys.numou.scheduling.domain.WorkPatternId;

public interface WorkPatternRepository extends Repository<WorkPattern, WorkPatternId> {}
