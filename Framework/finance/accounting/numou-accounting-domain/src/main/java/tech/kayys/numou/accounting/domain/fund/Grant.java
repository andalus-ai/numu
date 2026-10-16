package tech.kayys.syirkah.accounting.domain.fund;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Encapsulates an external donor grant with awarded budget and allowable expenditure limits.
 */
public final class Grant {
    private final GrantId id;
    private final String code;
    private final String donorName;
    private final FundId linkedFundId;
    private final BigDecimal awardedAmount;
    private BigDecimal expendedAmount;
    private final LocalDate expiryDate;

    public Grant(GrantId id, String code, String donorName, FundId linkedFundId, BigDecimal awardedAmount, LocalDate expiryDate) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.donorName = Objects.requireNonNull(donorName, "donorName must not be null");
        this.linkedFundId = Objects.requireNonNull(linkedFundId, "linkedFundId must not be null");
        this.awardedAmount = Objects.requireNonNull(awardedAmount, "awardedAmount must not be null");
        if (awardedAmount.signum() <= 0) throw new IllegalArgumentException("awardedAmount must be positive");
        this.expendedAmount = BigDecimal.ZERO;
        this.expiryDate = Objects.requireNonNull(expiryDate, "expiryDate must not be null");
    }

    public void recordExpenditure(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) throw new IllegalArgumentException("Expenditure must be positive");
        BigDecimal newExpended = expendedAmount.add(amount);
        if (newExpended.compareTo(awardedAmount) > 0) {
            throw new IllegalStateException("Expenditure exceeds grant award: awarded=" + awardedAmount + ", requested=" + newExpended);
        }
        this.expendedAmount = newExpended;
    }

    public BigDecimal remainingBalance() {
        return awardedAmount.subtract(expendedAmount);
    }

    public GrantId id() { return id; }
    public String code() { return code; }
    public String donorName() { return donorName; }
    public FundId linkedFundId() { return linkedFundId; }
    public BigDecimal awardedAmount() { return awardedAmount; }
    public BigDecimal expendedAmount() { return expendedAmount; }
    public LocalDate expiryDate() { return expiryDate; }
}
