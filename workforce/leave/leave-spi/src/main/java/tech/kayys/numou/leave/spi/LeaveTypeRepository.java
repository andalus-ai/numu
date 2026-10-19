package tech.kayys.numou.leave.spi;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.leave.domain.LeaveType;
import tech.kayys.numou.leave.domain.LeaveTypeId;

public interface LeaveTypeRepository extends Repository<LeaveType, LeaveTypeId> {}
