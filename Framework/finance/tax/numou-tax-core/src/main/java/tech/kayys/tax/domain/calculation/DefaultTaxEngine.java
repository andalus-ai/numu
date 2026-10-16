package tech.kayys.tax.domain.calculation;

import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.foundation.domain.valueobject.MonetaryRoundingPolicy;
import tech.kayys.tax.domain.category.TaxTreatment;
import tech.kayys.tax.domain.policy.TaxDeterminationService;
import tech.kayys.tax.domain.policy.TaxPolicy;
import tech.kayys.tax.domain.policy.TaxRate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Production implementation of transactional tax determination and calculation.
 */
public class DefaultTaxEngine {

    private final TaxDeterminationService determinationService;

    public DefaultTaxEngine(TaxDeterminationService determinationService) {
        this.determinationService = Objects.requireNonNull(determinationService, "determinationService cannot be null");
    }

    public TaxCalculationResult calculate(TaxCalculationRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        if (request.lines().isEmpty()) {
            throw new IllegalArgumentException("Cannot calculate tax for empty lines");
        }

        Currency currency = request.lines().getFirst().amount().currency();
        Money totalTaxableBase = Money.zero(currency);
        Money totalTax = Money.zero(currency);
        List<TaxLineResult> lineResults = new ArrayList<>();

        MonetaryRoundingPolicy rounding = request.roundingPolicy();

        for (TaxCalculationLine line : request.lines()) {
            if (!line.amount().currency().equals(currency)) {
                throw new IllegalArgumentException("Currency mismatch across lines: " + currency + " vs " + line.amount().currency());
            }

            TaxPolicy policy = determinationService.determinePolicy(line.context())
                    .orElseThrow(() -> new IllegalStateException(
                            "No applicable tax policy resolved for line: " + line.lineReference()
                    ));

            Money taxableBase;
            Money unroundedTax;
            Money roundedTax;

            BigDecimal rateVal = policy.rate().percentageValue();

            if (policy.treatment() == TaxTreatment.EXEMPT || policy.treatment() == TaxTreatment.ZERO_RATED || policy.treatment() == TaxTreatment.OUT_OF_SCOPE) {
                taxableBase = line.amount();
                unroundedTax = Money.zero(currency);
                roundedTax = Money.zero(currency);
            } else if (request.taxInclusive()) {
                // Included tax formula: Gross * (rate / (100 + rate))
                BigDecimal gross = line.amount().amount();
                BigDecimal divisor = new BigDecimal("100").add(rateVal);
                BigDecimal unroundedTaxAmount = gross.multiply(rateVal).divide(divisor, 8, RoundingMode.HALF_UP);
                BigDecimal roundedTaxAmount = unroundedTaxAmount.setScale(rounding.scale(), rounding.mode());

                unroundedTax = Money.of(unroundedTaxAmount, currency);
                roundedTax = Money.of(roundedTaxAmount, currency);
                taxableBase = Money.of(gross.subtract(roundedTaxAmount), currency);
            } else {
                // Tax-exclusive formula: Base * (rate / 100)
                taxableBase = line.amount();
                BigDecimal unroundedTaxAmount = taxableBase.amount().multiply(rateVal).divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
                BigDecimal roundedTaxAmount = unroundedTaxAmount.setScale(rounding.scale(), rounding.mode());

                unroundedTax = Money.of(unroundedTaxAmount, currency);
                roundedTax = Money.of(roundedTaxAmount, currency);
            }

            totalTaxableBase = totalTaxableBase.add(taxableBase);
            totalTax = totalTax.add(roundedTax);

            lineResults.add(new TaxLineResult(
                    line.lineReference(),
                    policy.code(),
                    policy.version(),
                    policy.treatment(),
                    taxableBase,
                    rateVal,
                    unroundedTax,
                    roundedTax
            ));
        }

        return new TaxCalculationResult(totalTaxableBase, totalTax, lineResults);
    }
}
