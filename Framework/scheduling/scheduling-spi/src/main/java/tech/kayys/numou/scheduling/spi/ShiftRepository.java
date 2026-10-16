package tech.kayys.numou.scheduling.spi;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.scheduling.domain.Shift;
import tech.kayys.numou.scheduling.domain.ShiftId;

public interface ShiftRepository extends Repository<Shift, ShiftId> {}
