package tech.kayys.numou.scheduling.spi;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.scheduling.domain.Schedule;
import tech.kayys.numou.scheduling.domain.ScheduleId;

public interface ScheduleRepository extends Repository<Schedule, ScheduleId> {}
