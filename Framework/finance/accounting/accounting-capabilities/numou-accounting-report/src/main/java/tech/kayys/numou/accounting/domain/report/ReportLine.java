package tech.kayys.numou.accounting.domain.report;

import tech.kayys.numou.accounting.domain.identifier.AccountId;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

public record ReportLine(
        AccountId accountId,
        String accountNumber,
        String accountName,
        Money amount
) implements ValueObject {}
