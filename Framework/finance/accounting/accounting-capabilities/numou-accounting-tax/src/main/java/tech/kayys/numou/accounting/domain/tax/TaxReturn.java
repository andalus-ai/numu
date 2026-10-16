package tech.kayys.syirkah.accounting.domain.tax;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Periodic tax return aggregate. */
public final class TaxReturn {

    private final TaxReturnId id;
    private final TaxKind kind;
    private final String taxPeriod;
    private final String jurisdiction;
    private BigDecimal totalOutputTax;
    private BigDecimal totalInputTax;
    private TaxReturnStatus status;
    private String filingReceipt;
    private Instant filedAt;

    public TaxReturn(TaxReturnId id, TaxKind kind, String taxPeriod, String jurisdiction) {
        this.id = Objects.requireNonNull(id);
        this.kind = Objects.requireNonNull(kind);
        this.taxPeriod = Objects.requireNonNull(taxPeriod);
        this.jurisdiction = Objects.requireNonNull(jurisdiction);
        this.totalOutputTax = BigDecimal.ZERO;
        this.totalInputTax = BigDecimal.ZERO;
        this.status = TaxReturnStatus.DRAFT;
    }

    public void updateTotals(BigDecimal outputTax, BigDecimal inputTax) {
        if (status != TaxReturnStatus.DRAFT) throw new IllegalStateException("Only DRAFT returns can be updated: " + status);
        this.totalOutputTax = Objects.requireNonNull(outputTax);
        this.totalInputTax = Objects.requireNonNull(inputTax);
        this.status = TaxReturnStatus.READY;
    }

    public BigDecimal netPayable() {
        return totalOutputTax.subtract(totalInputTax);
    }

    public void file(String receipt) {
        if (status != TaxReturnStatus.READY) throw new IllegalStateException("Only READY returns can be filed: " + status);
        this.filingReceipt = Objects.requireNonNull(receipt);
        this.status = TaxReturnStatus.FILED;
        this.filedAt = Instant.now();
    }

    public TaxReturnId id() { return id; }
    public TaxKind kind() { return kind; }
    public String taxPeriod() { return taxPeriod; }
    public String jurisdiction() { return jurisdiction; }
    public BigDecimal totalOutputTax() { return totalOutputTax; }
    public BigDecimal totalInputTax() { return totalInputTax; }
    public TaxReturnStatus status() { return status; }
    public String filingReceipt() { return filingReceipt; }
    public Instant filedAt() { return filedAt; }
}
