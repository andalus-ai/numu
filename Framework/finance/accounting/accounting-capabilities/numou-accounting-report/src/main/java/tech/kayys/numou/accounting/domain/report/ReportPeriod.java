package tech.kayys.numou.accounting.domain.report;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;
import java.time.LocalDate;
import java.util.Objects;

public record ReportPeriod(LocalDate startDate, LocalDate endDate, String periodLabel) implements ValueObject {
    public ReportPeriod {
        Objects.requireNonNull(startDate, "startDate cannot be null");
        Objects.requireNonNull(endDate, "endDate cannot be null");
    }
}
