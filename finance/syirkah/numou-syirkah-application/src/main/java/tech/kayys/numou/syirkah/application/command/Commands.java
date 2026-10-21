package tech.kayys.numou.numouapplication.command;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.numoudomain.CloseId;
import tech.kayys.numou.numoudomain.FinancialResult;
import tech.kayys.numou.numoudomain.LiquidationInput;
import tech.kayys.numou.numoudomain.ManagerFault;
import tech.kayys.numou.numoudomain.PartnerId;
import tech.kayys.numou.numoudomain.ProfitDisposition;
import tech.kayys.numou.numoudomain.SyirkahContract;
import tech.kayys.numou.numoudomain.SyirkahId;

import java.time.LocalDate;
import java.util.Set;

/** Command objects of the syirkah use cases. */
public final class Commands {

    private Commands() {
    }

    public record Open(TenantId tenant, SyirkahId id, SyirkahContract contract, int amountScale) {
    }

    public record ClosePeriod(TenantId tenant, SyirkahId id, CloseId closeId, LocalDate start, LocalDate end,
            FinancialResult result, ManagerFault fault, ProfitDisposition disposition) {
    }

    public record DistributeProfit(TenantId tenant, SyirkahId id, PartnerId partner, Money amount,
            String reference) {
    }

    public record SettleLiability(TenantId tenant, SyirkahId id, PartnerId partner, Money amount,
            String reference) {
    }

    public record ReviseContract(TenantId tenant, SyirkahId id, SyirkahContract next, Set<PartnerId> approvals) {
    }

    public record CorrectClose(TenantId tenant, SyirkahId id, CloseId target, CloseId replacement,
            FinancialResult corrected, ManagerFault fault, ProfitDisposition disposition) {
    }

    public record StartLiquidation(TenantId tenant, SyirkahId id) {
    }

    public record Liquidate(TenantId tenant, SyirkahId id, LiquidationInput input, ManagerFault fault) {
    }
}
