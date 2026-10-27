package tech.kayys.numou.numouapplication.governance;

import tech.kayys.numou.syirkah.domain.SyirkahNotFoundException;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numouapplication.SyirkahApplicationService;
import tech.kayys.numou.numouapplication.command.Commands;
import tech.kayys.numou.numouapplication.port.ApprovalPort;
import tech.kayys.numou.numouapplication.port.TransactionPort;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Four-eyes control for the irreversible operations: akad revision, close
 * correction and liquidation.
 * A maker proposes; a different checker approves (which executes the exact
 * stored command) or rejects.
 * Routine operations (close, distribute, settle) stay on
 * {@link SyirkahApplicationService}.
 */
public class GovernedSyirkahService {

    private final SyirkahApplicationService service;
    private final ApprovalPort approvals;
    private final TransactionPort tx;
    private final Clock clock;

    public GovernedSyirkahService(SyirkahApplicationService service, ApprovalPort approvals, TransactionPort tx,
            Clock clock) {
        this.service = Objects.requireNonNull(service);
        this.approvals = Objects.requireNonNull(approvals);
        this.tx = Objects.requireNonNull(tx);
        this.clock = Objects.requireNonNull(clock);
    }

    public PendingApproval proposeRevision(Commands.ReviseContract c, String maker) {
        return propose(c.tenant(), c.id(), PendingApproval.Kind.REVISE_CONTRACT, c, maker);
    }

    public PendingApproval proposeCorrection(Commands.CorrectClose c, String maker) {
        return propose(c.tenant(), c.id(), PendingApproval.Kind.CORRECT_CLOSE, c, maker);
    }

    public PendingApproval proposeStartLiquidation(Commands.StartLiquidation c, String maker) {
        return propose(c.tenant(), c.id(), PendingApproval.Kind.START_LIQUIDATION, c, maker);
    }

    public PendingApproval proposeLiquidation(Commands.Liquidate c, String maker) {
        return propose(c.tenant(), c.id(), PendingApproval.Kind.LIQUIDATE, c, maker);
    }

    public List<PendingApproval> pending(TenantId tenant) {
        return approvals.pending(tenant);
    }

    /**
     * Approves and executes. The checker must differ from the maker
     * (case-insensitive).
     */
    public PendingApproval approve(TenantId tenant, UUID id, String checker) {
        return tx.inTransaction(() -> {
            PendingApproval p = load(tenant, id);
            requirePending(p);
            requireDifferentPerson(p, checker);
            switch (p.kind()) {
                case REVISE_CONTRACT -> service.reviseContract((Commands.ReviseContract) p.payload());
                case CORRECT_CLOSE -> service.correctClose((Commands.CorrectClose) p.payload());
                case START_LIQUIDATION -> service.startLiquidation((Commands.StartLiquidation) p.payload());
                case LIQUIDATE -> service.liquidate((Commands.Liquidate) p.payload());
            }
            PendingApproval done = p.decided(PendingApproval.Status.APPROVED, checker, clock.instant(), null);
            approvals.save(done);
            return done;
        });
    }

    public PendingApproval reject(TenantId tenant, UUID id, String checker, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleViolation("a rejection must state its reason");
        }
        return tx.inTransaction(() -> {
            PendingApproval p = load(tenant, id);
            requirePending(p);
            requireDifferentPerson(p, checker);
            PendingApproval done = p.decided(PendingApproval.Status.REJECTED, checker, clock.instant(), reason);
            approvals.save(done);
            return done;
        });
    }

    private PendingApproval propose(TenantId tenant, tech.kayys.numou.numoudomain.SyirkahId syirkah,
            PendingApproval.Kind kind, Object payload, String maker) {
        Instant now = clock.instant();
        PendingApproval p = new PendingApproval(UUID.randomUUID(), tenant, syirkah, kind, payload, maker, now,
                PendingApproval.Status.PENDING, null, null, null);
        return tx.inTransaction(() -> {
            // fail fast if the syirkah does not exist for this tenant
            service.get(tenant, syirkah);
            approvals.save(p);
            return p;
        });
    }

    private PendingApproval load(TenantId tenant, UUID id) {
        return approvals.find(tenant, id).orElseThrow(() -> new SyirkahNotFoundException("approval not found: " + id));
    }

    private static void requirePending(PendingApproval p) {
        if (p.status() != PendingApproval.Status.PENDING) {
            throw new BusinessRuleViolation("approval already " + p.status());
        }
    }

    private static void requireDifferentPerson(PendingApproval p, String checker) {
        if (checker == null || checker.isBlank()) {
            throw new BusinessRuleViolation("checker must be identified");
        }
        if (checker.trim().equalsIgnoreCase(p.maker().trim())) {
            throw new BusinessRuleViolation("maker and checker must be different people");
        }
    }
}
