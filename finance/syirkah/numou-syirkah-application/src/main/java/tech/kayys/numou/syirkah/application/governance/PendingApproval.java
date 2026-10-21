package tech.kayys.numou.numouapplication.governance;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numoudomain.SyirkahId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A sensitive command waiting for a second person. The payload is the original
 * command object, so what the
 * checker approves is exactly what is executed.
 */
public record PendingApproval(UUID id, TenantId tenant, SyirkahId syirkahId, Kind kind, Object payload, String maker,
        Instant proposedAt, Status status, String checker, Instant decidedAt, String note) {

    public enum Kind {
        REVISE_CONTRACT, CORRECT_CLOSE, START_LIQUIDATION, LIQUIDATE
    }

    public enum Status {
        PENDING, APPROVED, REJECTED
    }

    public PendingApproval {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(syirkahId);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(status);
        if (maker == null || maker.isBlank()) {
            throw new IllegalArgumentException("maker must not be blank");
        }
    }

    PendingApproval decided(Status newStatus, String by, Instant at, String why) {
        return new PendingApproval(id, tenant, syirkahId, kind, payload, maker, proposedAt, newStatus, by, at, why);
    }
}
