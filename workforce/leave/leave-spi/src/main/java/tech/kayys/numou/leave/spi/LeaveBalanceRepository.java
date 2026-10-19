package tech.kayys.numou.leave.spi;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.leave.domain.LeaveBalance;
import tech.kayys.numou.leave.domain.LeaveBalanceId;

public interface LeaveBalanceRepository extends Repository<LeaveBalance, LeaveBalanceId> {}
