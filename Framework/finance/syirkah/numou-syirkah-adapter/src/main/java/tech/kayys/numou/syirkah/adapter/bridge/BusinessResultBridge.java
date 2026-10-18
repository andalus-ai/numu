package tech.kayys.numou.syirkah.adapter.bridge;

import tech.kayys.numou.accounting.domain.islamic.SyirkahBusinessResult;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.syirkah.application.command.Commands;
import tech.kayys.numou.syirkah.domain.CloseId;
import tech.kayys.numou.syirkah.domain.FinancialResult;
import tech.kayys.numou.syirkah.domain.ManagerFault;
import tech.kayys.numou.syirkah.domain.PartnerId;
import tech.kayys.numou.syirkah.domain.ProfitDisposition;
import tech.kayys.numou.syirkah.domain.SyirkahId;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Seam between the accounting module's contract/calculation front end (string ids, {@link SyirkahBusinessResult})
 * and the syirkah ledger (UUID ids, {@link Commands.ClosePeriod}). Mapping is deterministic, so retrying the
 * same business result always addresses the same ledger identifiers.
 */
public final class BusinessResultBridge {

    private BusinessResultBridge() {
    }

    /** Stable UUID for a contract id string (name-based, v3). */
    public static SyirkahId syirkahId(String tenantId, String companyId, String contractId) {
        return new SyirkahId(nameUuid("syirkah|" + tenantId + "|" + companyId + "|" + contractId));
    }

    /** Stable UUID for a party id string. */
    public static PartnerId partnerId(String contractId, String partyId) {
        return new PartnerId(nameUuid("partner|" + contractId + "|" + partyId));
    }

    /** One close per business result: the result id fixes the close id (retry-safe, double close impossible). */
    public static CloseId closeId(SyirkahBusinessResult result) {
        return new CloseId(nameUuid("close|" + result.contractId() + "|" + result.resultId()));
    }

    public static FinancialResult toFinancialResult(SyirkahBusinessResult r) {
        Currency c = Currency.of(r.currencyCode());
        return FinancialResult.of(Money.of(r.totalRevenue(), c), Money.of(r.directCosts(), c),
                Money.of(r.operatingCosts(), c), Money.of(r.otherCosts(), c));
    }

    public static Commands.ClosePeriod toClosePeriod(TenantId tenant, String companyId, SyirkahBusinessResult r,
                                                     ManagerFault fault, ProfitDisposition disposition) {
        Objects.requireNonNull(disposition, "disposition");
        SyirkahId id = syirkahId(tenant.value().toString(), companyId, r.contractId());
        return new Commands.ClosePeriod(tenant, id, closeId(r), r.periodStart(), r.periodEnd(),
                toFinancialResult(r), fault == null ? ManagerFault.none() : fault, disposition);
    }

    private static UUID nameUuid(String s) {
        return UUID.nameUUIDFromBytes(s.getBytes(StandardCharsets.UTF_8));
    }
}
