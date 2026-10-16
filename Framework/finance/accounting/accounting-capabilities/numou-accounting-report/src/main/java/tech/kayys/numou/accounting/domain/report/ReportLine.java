package tech.kayys.syirkah.accounting.domain.report;

import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

public record ReportLine(
        AccountId accountId,
        String accountNumber,
        String accountName,
        Money amount
) implements ValueObject {}
