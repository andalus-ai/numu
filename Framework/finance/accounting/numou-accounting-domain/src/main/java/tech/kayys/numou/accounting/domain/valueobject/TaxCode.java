package tech.kayys.syirkah.accounting.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.Percentage;
import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Tax code representation in Accounting.
 */
public record TaxCode(
        String code,
        String description,
        Percentage rate
) implements ValueObject {

    public TaxCode {
        Objects.requireNonNull(code, "Tax code cannot be null");
        Objects.requireNonNull(rate, "Tax rate cannot be null");
    }

    public static TaxCode standard(String code, double ratePercentage) {
        return new TaxCode(code, "Tax " + code, Percentage.of(ratePercentage));
    }

    public static TaxCode ppn11() {
        return new TaxCode("PPN11", "Pajak Pertambahan Nilai 11%", Percentage.of(BigDecimal.valueOf(11)));
    }

    public static TaxCode ppn12() {
        return new TaxCode("PPN12", "Pajak Pertambahan Nilai 12%", Percentage.of(BigDecimal.valueOf(12)));
    }

    public static TaxCode exempt() {
        return new TaxCode("EXEMPT", "Tax Exempt", Percentage.zero());
    }
}
