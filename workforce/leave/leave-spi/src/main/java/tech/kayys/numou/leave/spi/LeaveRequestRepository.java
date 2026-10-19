package tech.kayys.numou.leave.spi;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.leave.domain.LeaveRequest;
import tech.kayys.numou.leave.domain.LeaveRequestId;

public interface LeaveRequestRepository extends Repository<LeaveRequest, LeaveRequestId> {}
