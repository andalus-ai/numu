package tech.kayys.numou.syirkah.application.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.time.LocalDate;
import java.util.List;

/**
 * Posting port to the general ledger. Must be idempotent on {@code sourceKey}: posting the same key twice
 * creates one journal entry.
 */
public interface JournalPostingPort {

    void post(Request request);

    record Request(TenantId tenant, String sourceKey, LocalDate date, String description, List<Line> lines) {
    }

    record Line(String accountCode, Money debit, Money credit, String memo) {
    }
}
