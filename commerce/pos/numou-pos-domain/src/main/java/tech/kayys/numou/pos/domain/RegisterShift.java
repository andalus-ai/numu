package tech.kayys.numou.pos.domain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of a cashier's register shift.
 *
 * <p>Closing a shift records the counted cash; reconciliation against expected
 * cash and posting to finance remain explicit application workflows.</p>
 */
public record RegisterShift(
        UUID id,
        UUID tenantId,
        UUID registerId,
        UUID operatorId,
        Instant openedAt,
        Money openingFloat,
        Status status,
        Instant closedAt,
        Money countedCash) {

    public enum Status {
        OPEN,
        CLOSED
    }

    public RegisterShift {
        Objects.requireNonNull(id, "shift id cannot be null");
        Objects.requireNonNull(tenantId, "tenant id cannot be null");
        Objects.requireNonNull(registerId, "register id cannot be null");
        Objects.requireNonNull(operatorId, "operator id cannot be null");
        Objects.requireNonNull(openedAt, "openedAt cannot be null");
        Objects.requireNonNull(openingFloat, "opening float cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        if (openingFloat.isNegative()) {
            throw new IllegalArgumentException("opening float cannot be negative");
        }
        if (status == Status.OPEN && (closedAt != null || countedCash != null)) {
            throw new IllegalArgumentException("open shift cannot have close details");
        }
        if (status == Status.CLOSED) {
            Objects.requireNonNull(closedAt, "closedAt cannot be null for a closed shift");
            Objects.requireNonNull(countedCash, "countedCash cannot be null for a closed shift");
            if (closedAt.isBefore(openedAt)) {
                throw new IllegalArgumentException("closedAt cannot be before openedAt");
            }
            if (countedCash.isNegative()) {
                throw new IllegalArgumentException("counted cash cannot be negative");
            }
            if (!openingFloat.currency().equals(countedCash.currency())) {
                throw new IllegalArgumentException("opening float and counted cash must use the same currency");
            }
        }
    }

    public static RegisterShift open(
            UUID id,
            UUID tenantId,
            UUID registerId,
            UUID operatorId,
            Instant openedAt,
            Money openingFloat) {
        return new RegisterShift(id, tenantId, registerId, operatorId, openedAt,
                openingFloat, Status.OPEN, null, null);
    }

    public RegisterShift close(Instant closedAt, Money countedCash) {
        if (status != Status.OPEN) {
            throw new IllegalStateException("shift is already closed");
        }
        return new RegisterShift(id, tenantId, registerId, operatorId, openedAt,
                openingFloat, Status.CLOSED, closedAt, countedCash);
    }
}
