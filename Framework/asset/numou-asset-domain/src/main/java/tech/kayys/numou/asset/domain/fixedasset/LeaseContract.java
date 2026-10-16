package tech.kayys.syirkah.asset.domain.fixedasset;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * An IFRS 16 / ASC 842 compliant lease contract.
 */
public final class LeaseContract {

    private final LeaseId id;
    private final String contractNumber;
    private final String lessor;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final int termMonths;
    private final BigDecimal monthlyPayment;
    private final BigDecimal discountRateAnnual; // e.g. 0.05 for 5%
    private final LeaseClassification classification;

    private BigDecimal leaseLiability;
    private BigDecimal rightOfUseAssetValue;
    private BigDecimal accumulatedRouDepreciation;

    public LeaseContract(LeaseId id, String contractNumber, String lessor,
                         LocalDate startDate, int termMonths,
                         BigDecimal monthlyPayment, BigDecimal discountRateAnnual,
                         LeaseClassification classification) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.contractNumber = Objects.requireNonNull(contractNumber, "contractNumber must not be null");
        this.lessor = Objects.requireNonNull(lessor, "lessor must not be null");
        this.startDate = Objects.requireNonNull(startDate, "startDate must not be null");
        this.termMonths = termMonths;
        this.monthlyPayment = Objects.requireNonNull(monthlyPayment, "monthlyPayment must not be null");
        this.discountRateAnnual = Objects.requireNonNull(discountRateAnnual, "discountRateAnnual must not be null");
        this.classification = Objects.requireNonNull(classification, "classification must not be null");
        this.endDate = startDate.plusMonths(termMonths);

        if (termMonths <= 0) throw new IllegalArgumentException("termMonths must be > 0");
        if (monthlyPayment.signum() <= 0) throw new IllegalArgumentException("monthlyPayment must be > 0");

        // Compute Present Value of Lease Payments = Lease Liability & Initial ROU Asset Value
        BigDecimal pv = calculatePresentValue(monthlyPayment, discountRateAnnual, termMonths);
        this.leaseLiability = pv;
        this.rightOfUseAssetValue = pv;
        this.accumulatedRouDepreciation = BigDecimal.ZERO;
    }

    /**
     * Approximates annuity present value: PV = P * [1 - (1 + r)^(-n)] / r
     */
    private static BigDecimal calculatePresentValue(BigDecimal pmt, BigDecimal annualRate, int months) {
        if (annualRate.signum() == 0) {
            return pmt.multiply(BigDecimal.valueOf(months));
        }
        double monthlyRate = annualRate.doubleValue() / 12.0;
        double pvDouble = pmt.doubleValue() * ((1.0 - Math.pow(1.0 + monthlyRate, -months)) / monthlyRate);
        return BigDecimal.valueOf(pvDouble).setScale(2, RoundingMode.HALF_UP);
    }

    public void amortizePeriod(BigDecimal interestExpense, BigDecimal principalReduction, BigDecimal rouDepreciation) {
        this.leaseLiability = this.leaseLiability.subtract(principalReduction).max(BigDecimal.ZERO);
        this.accumulatedRouDepreciation = this.accumulatedRouDepreciation.add(rouDepreciation);
    }

    public LeaseId id() { return id; }
    public String contractNumber() { return contractNumber; }
    public String lessor() { return lessor; }
    public LocalDate startDate() { return startDate; }
    public LocalDate endDate() { return endDate; }
    public int termMonths() { return termMonths; }
    public BigDecimal monthlyPayment() { return monthlyPayment; }
    public BigDecimal discountRateAnnual() { return discountRateAnnual; }
    public LeaseClassification classification() { return classification; }
    public BigDecimal leaseLiability() { return leaseLiability; }
    public BigDecimal rightOfUseAssetValue() { return rightOfUseAssetValue; }
    public BigDecimal accumulatedRouDepreciation() { return accumulatedRouDepreciation; }
    public BigDecimal rouNetBookValue() { return rightOfUseAssetValue.subtract(accumulatedRouDepreciation); }
}
