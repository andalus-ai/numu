package tech.kayys.numou.pos.application.port.out;

import java.util.UUID;

/** Verifies that the operator may transact on the specified open shift. */
@FunctionalInterface
public interface OpenShiftAccess {
    void requireOpenShift(UUID tenantId, UUID branchId, UUID registerId, UUID shiftId, UUID operatorId);
}
