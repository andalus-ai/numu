
package tech.kayys.syirkah.accounting.domain.consolidation;

import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.Objects;

public record EliminationEntry(
        String eliminationId,
        String ruleCode,
        AccountId debitAccountId,
        AccountId creditAccountId,
        Money amount,
        String description
) {
    public EliminationEntry {
        Objects.requireNonNull(eliminationId, "eliminationId cannot be null");
        Objects.requireNonNull(ruleCode, "ruleCode cannot be null");
        Objects.requireNonNull(debitAccountId, "debitAccountId cannot be null");
        Objects.requireNonNull(creditAccountId, "creditAccountId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
    }
}
