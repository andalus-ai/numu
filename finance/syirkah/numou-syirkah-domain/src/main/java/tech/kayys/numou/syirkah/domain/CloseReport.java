package tech.kayys.numou.numoudomain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * Human-readable view of one period close: result build-up, frozen terms and
 * per-partner outcome.
 */
public record CloseReport(CloseId closeId, int sequence, LocalDate periodStart, LocalDate periodEnd,
        int contractVersion, CloseStatus status, FinancialResult result, Money netResult,
        ProfitDisposition disposition, List<Row> rows, Money totalProfitShare,
        Money totalCapitalRestored, Money totalLoss, Money totalManagerLiability) implements Serializable {

    public CloseReport {
        rows = List.copyOf(rows);
    }

    public record Row(PartnerId partner, String name, Money capitalRestored, Money profitShare, Money retained,
            Money payable, Money lossBorne, Money managerLiability) implements Serializable {
    }
}
