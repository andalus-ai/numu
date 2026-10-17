package tech.kayys.numou.accounting.domain.islamic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * Evidence-backed responsibility assessment for a Syirkah loss.
 *
 * <p>This aggregate deliberately does not create a receivable, expense, or journal.
 * A determined and approved responsibility is still only a business decision; a
 * separate accounting-policy and posting workflow must decide its financial treatment.</p>
 */
public final class SyirkahLossResponsibility {
    private final String responsibilityId;
    private final String tenantId;
    private final String companyId;
    private final String contractId;
    private final int contractVersion;
    private final String resultId;
    private final String currencyCode;
    private final BigDecimal lossAmount;
    private final String reportedBy;
    private final Instant reportedAt;
    private SyirkahLossCause cause;
    private String responsiblePartyId;
    private List<String> evidenceReferences = List.of();
    private String determinationReference;
    private String determinedBy;
    private Instant determinedAt;
    private String approvedBy;
    private Instant approvedAt;
    private String postedJournalReference;
    private BigDecimal settledAmount;
    private SyirkahLossResponsibilityStatus status;

    public SyirkahLossResponsibility(String responsibilityId, String tenantId, String companyId,
                                    String contractId, int contractVersion, String resultId,
                                    String currencyCode, BigDecimal lossAmount, String reportedBy) {
        this.responsibilityId = requireText(responsibilityId, "responsibilityId");
        this.tenantId = requireText(tenantId, "tenantId");
        this.companyId = requireText(companyId, "companyId");
        this.contractId = requireText(contractId, "contractId");
        if (contractVersion < 1) throw new IllegalArgumentException("contractVersion must be >= 1");
        this.contractVersion = contractVersion;
        this.resultId = requireText(resultId, "resultId");
        this.currencyCode = requireText(currencyCode, "currencyCode").toUpperCase(java.util.Locale.ROOT);
        Currency currency = Currency.getInstance(this.currencyCode);
        int scale = currency.getDefaultFractionDigits();
        if (scale < 0) throw new IllegalArgumentException("currency must have a standard minor unit");
        Objects.requireNonNull(lossAmount, "lossAmount");
        if (lossAmount.signum() <= 0) throw new IllegalArgumentException("lossAmount must be positive");
        this.lossAmount = lossAmount.setScale(scale, RoundingMode.UNNECESSARY);
        this.reportedBy = requireText(reportedBy, "reportedBy");
        this.reportedAt = Instant.now();
        this.settledAmount = BigDecimal.ZERO.setScale(scale);
        this.status = SyirkahLossResponsibilityStatus.REPORTED;
    }

    /** Independent reviewer records a determination with evidence and a decision reference. */
    public void determine(SyirkahLossCause cause, String responsiblePartyId, List<String> evidenceReferences,
                          String determinationReference, String reviewer) {
        requireStatus(SyirkahLossResponsibilityStatus.REPORTED);
        this.cause = Objects.requireNonNull(cause, "cause");
        this.evidenceReferences = List.copyOf(Objects.requireNonNull(evidenceReferences, "evidenceReferences"));
        if (this.evidenceReferences.isEmpty() || this.evidenceReferences.stream().anyMatch(e -> e == null || e.isBlank())) {
            throw new IllegalArgumentException("at least one valid evidence reference is required");
        }
        this.determinationReference = requireText(determinationReference, "determinationReference");
        this.determinedBy = requireText(reviewer, "reviewer");
        if (reportedBy.equals(reviewer)) throw new SecurityException("Reporter cannot determine their own loss responsibility");
        if (cause == SyirkahLossCause.NEGLIGENCE || cause == SyirkahLossCause.MISCONDUCT
                || cause == SyirkahLossCause.BREACH_OF_CONTRACT) {
            this.responsiblePartyId = requireText(responsiblePartyId, "responsiblePartyId");
        } else {
            this.responsiblePartyId = null;
        }
        this.determinedAt = Instant.now();
        this.status = SyirkahLossResponsibilityStatus.DETERMINED;
    }

    /** A second person approves the determination; approval still does not post a receivable. */
    public void approve(String approver) {
        requireStatus(SyirkahLossResponsibilityStatus.DETERMINED);
        String checked = requireText(approver, "approver");
        if (checked.equals(reportedBy) || checked.equals(determinedBy)) {
            throw new SecurityException("Reporter and determiner cannot approve the responsibility assessment");
        }
        this.approvedBy = checked;
        this.approvedAt = Instant.now();
        this.status = SyirkahLossResponsibilityStatus.APPROVED;
    }

    /** Marks posting only after a separate ledger adapter confirms a committed journal reference. */
    public void markPosted(String journalReference) {
        requireStatus(SyirkahLossResponsibilityStatus.APPROVED);
        this.postedJournalReference = requireText(journalReference, "journalReference");
        this.status = SyirkahLossResponsibilityStatus.POSTED;
    }

    /** Records a reconciled recovery amount. This does not execute a payment. */
    public void recordSettlement(BigDecimal amount) {
        if (status != SyirkahLossResponsibilityStatus.POSTED
                && status != SyirkahLossResponsibilityStatus.PARTIALLY_SETTLED) {
            throw new IllegalStateException("Only posted responsibility amounts may be settled");
        }
        Objects.requireNonNull(amount, "amount");
        int scale = Currency.getInstance(currencyCode).getDefaultFractionDigits();
        BigDecimal normalized = amount.setScale(scale, RoundingMode.UNNECESSARY);
        if (normalized.signum() <= 0) throw new IllegalArgumentException("settlement amount must be positive");
        BigDecimal next = settledAmount.add(normalized);
        if (next.compareTo(lossAmount) > 0) throw new IllegalArgumentException("settlements exceed assessed loss amount");
        settledAmount = next;
        status = next.compareTo(lossAmount) == 0
                ? SyirkahLossResponsibilityStatus.SETTLED
                : SyirkahLossResponsibilityStatus.PARTIALLY_SETTLED;
    }

    private void requireStatus(SyirkahLossResponsibilityStatus expected) {
        if (status != expected) throw new IllegalStateException("Expected " + expected + " but was " + status);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    public String responsibilityId() { return responsibilityId; }
    public String tenantId() { return tenantId; }
    public String companyId() { return companyId; }
    public String contractId() { return contractId; }
    public int contractVersion() { return contractVersion; }
    public String resultId() { return resultId; }
    public String currencyCode() { return currencyCode; }
    public BigDecimal lossAmount() { return lossAmount; }
    public String reportedBy() { return reportedBy; }
    public Instant reportedAt() { return reportedAt; }
    public SyirkahLossCause cause() { return cause; }
    public String responsiblePartyId() { return responsiblePartyId; }
    public List<String> evidenceReferences() { return evidenceReferences; }
    public String determinationReference() { return determinationReference; }
    public String determinedBy() { return determinedBy; }
    public Instant determinedAt() { return determinedAt; }
    public String approvedBy() { return approvedBy; }
    public Instant approvedAt() { return approvedAt; }
    public String postedJournalReference() { return postedJournalReference; }
    public BigDecimal settledAmount() { return settledAmount; }
    public SyirkahLossResponsibilityStatus status() { return status; }
}
