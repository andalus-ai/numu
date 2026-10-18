package tech.kayys.numou.syirkah.adapter;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.syirkah.adapter.memory.*;
import tech.kayys.numou.syirkah.application.SyirkahAccountMap;
import tech.kayys.numou.syirkah.application.SyirkahApplicationService;
import tech.kayys.numou.syirkah.application.command.Commands;
import tech.kayys.numou.syirkah.application.governance.GovernedSyirkahService;
import tech.kayys.numou.syirkah.application.governance.PendingApproval;
import tech.kayys.numou.syirkah.domain.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MakerCheckerTest {

    private static final PartnerId A = PartnerId.of("00000000-0000-0000-0000-00000000000a");
    private static final PartnerId B = PartnerId.of("00000000-0000-0000-0000-00000000000b");

    private final TenantId tenant = TenantId.generate();
    private final LocalTransactionPort tx = new LocalTransactionPort();
    private final SyirkahApplicationService service = new SyirkahApplicationService(new InMemorySyirkahRepository(),
            new InMemoryJournalPostingPort(), new CollectingEventPublisher(), tx, new SyirkahAccountMap(Map.of(
            SyirkahPostingPlan.AccountRole.CASH, "1100", SyirkahPostingPlan.AccountRole.PARTNER_CAPITAL, "3100",
            SyirkahPostingPlan.AccountRole.PROFIT_PAYABLE, "2300", SyirkahPostingPlan.AccountRole.PERIOD_RESULT, "3900",
            SyirkahPostingPlan.AccountRole.PARTNER_RECEIVABLE, "1300")));
    private final GovernedSyirkahService governed =
            new GovernedSyirkahService(service, new InMemoryApprovalPort(), tx, Clock.systemUTC());

    private static Money idr(long v) {
        return Money.of(BigDecimal.valueOf(v), "IDR");
    }

    private SyirkahId open() {
        Map<PartnerId, BigDecimal> n = new TreeMap<>();
        n.put(A, new BigDecimal("50"));
        n.put(B, new BigDecimal("50"));
        SyirkahContract c = new SyirkahContract(1, SyirkahType.INAN, Currency.of("IDR"),
                List.of(new Partner(A, "A", PartnerRole.PARTNER, true), new Partner(B, "B", PartnerRole.PARTNER, true)),
                List.of(CapitalContribution.cash(A, idr(100)), CapitalContribution.cash(B, idr(100))),
                ProfitSharingRatio.ofPercent(n), ProfitBasis.NET_PROFIT, null, Set.of(), List.of("W1", "W2"), true,
                LocalDate.parse("2026-01-01"));
        SyirkahId id = SyirkahId.generate();
        service.open(new Commands.Open(tenant, id, c, 0));
        return id;
    }

    @Test
    void makerCannotApproveOwnProposalAndNothingRuns() {
        SyirkahId id = open();
        PendingApproval p = governed.proposeStartLiquidation(new Commands.StartLiquidation(tenant, id), "Alice");
        assertThrows(BusinessRuleViolation.class, () -> governed.approve(tenant, p.id(), " alice "));
        assertEquals(SyirkahStatus.ACTIVE, service.get(tenant, id).status());
        assertEquals(1, governed.pending(tenant).size());
    }

    @Test
    void checkerApprovalExecutesTheStoredCommandExactlyOnce() {
        SyirkahId id = open();
        PendingApproval p = governed.proposeStartLiquidation(new Commands.StartLiquidation(tenant, id), "Alice");
        PendingApproval done = governed.approve(tenant, p.id(), "Bob");
        assertEquals(PendingApproval.Status.APPROVED, done.status());
        assertEquals("Bob", done.checker());
        assertEquals(SyirkahStatus.LIQUIDATING, service.get(tenant, id).status());
        assertThrows(BusinessRuleViolation.class, () -> governed.approve(tenant, p.id(), "Carol"));
        assertTrue(governed.pending(tenant).isEmpty());
    }

    @Test
    void rejectionNeedsReasonAndNeverExecutes() {
        SyirkahId id = open();
        PendingApproval p = governed.proposeStartLiquidation(new Commands.StartLiquidation(tenant, id), "Alice");
        assertThrows(BusinessRuleViolation.class, () -> governed.reject(tenant, p.id(), "Bob", " "));
        PendingApproval r = governed.reject(tenant, p.id(), "Bob", "not agreed by all partners");
        assertEquals(PendingApproval.Status.REJECTED, r.status());
        assertEquals(SyirkahStatus.ACTIVE, service.get(tenant, id).status());
        assertThrows(BusinessRuleViolation.class, () -> governed.approve(tenant, p.id(), "Carol"));
    }

    @Test
    void liquidationGoesThroughTwoApprovalsAndSettles() {
        SyirkahId id = open();
        governed.approve(tenant, governed.proposeStartLiquidation(new Commands.StartLiquidation(tenant, id), "Alice").id(), "Bob");
        PendingApproval liq = governed.proposeLiquidation(new Commands.Liquidate(tenant, id,
                new LiquidationInput(idr(260), idr(20), true, LocalDate.parse("2026-12-31")), ManagerFault.none()), "Alice");
        governed.approve(tenant, liq.id(), "Bob");
        Syirkah s = service.get(tenant, id);
        assertEquals(SyirkahStatus.TERMINATED, s.status());
        assertEquals(0, new BigDecimal("40").compareTo(s.liquidation().netResult().amount())); // 240 - 200
        assertTrue(s.reconcile().balanced());
    }

    @Test
    void otherTenantCannotSeeOrDecideApprovals() {
        SyirkahId id = open();
        PendingApproval p = governed.proposeStartLiquidation(new Commands.StartLiquidation(tenant, id), "Alice");
        TenantId other = TenantId.generate();
        assertTrue(governed.pending(other).isEmpty());
        assertThrows(BusinessRuleViolation.class, () -> governed.approve(other, p.id(), "Mallory"));
        assertThrows(BusinessRuleViolation.class, () -> governed.proposeStartLiquidation(
                new Commands.StartLiquidation(other, id), "Mallory"));
    }

    @Test
    void failedExecutionLeavesApprovalPending() {
        SyirkahId id = open();
        // liquidating an ACTIVE syirkah without starting it fails at execution time
        PendingApproval p = governed.proposeLiquidation(new Commands.Liquidate(tenant, id,
                new LiquidationInput(idr(10), idr(0), true, LocalDate.parse("2026-12-31")), ManagerFault.none()), "Alice");
        assertThrows(RuntimeException.class, () -> governed.approve(tenant, p.id(), "Bob"));
        assertEquals(PendingApproval.Status.PENDING, governed.pending(tenant).get(0).status());
    }
}
