package tech.kayys.numou.accounting.domain.islamic.syirkah;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable lifecycle aggregate for one partner distribution obligation.
 * Journal posting and settlement execution are external effects; this aggregate
 * records their verified references and enforces legal state transitions.
 */
public final class PartnerDistributionObligation {
    public enum Status { PREPARED, APPROVED, POSTED, PARTIALLY_SETTLED, SETTLED }
    public static final String POSTING_SOURCE_TYPE = "SYIRKAH_DISTRIBUTION";

    private final String tenantId;
    private final String ledgerId;
    private final String obligationId;
    private final String closeCycleId;
    private final String calculationId;
    private final String calculationHash;
    private final String contractId;
    private final int contractVersion;
    private final String partyId;
    private final BigDecimal amount;
    private final String currencyCode;
    private final int currencyScale;
    private final String preparedBy;
    private final Instant preparedAt;
    private final String approvedBy;
    private final Instant approvedAt;
    private final String journalEntryId;
    private final String postedBy;
    private final Instant postedAt;
    private final List<DistributionSettlement> settlements;
    private final long revision;

    private PartnerDistributionObligation(
            String tenantId, String ledgerId, String obligationId, String closeCycleId,
            String calculationId, String calculationHash, String contractId, int contractVersion,
            String partyId, BigDecimal amount, String currencyCode, int currencyScale,
            String preparedBy, Instant preparedAt, String approvedBy, Instant approvedAt,
            String journalEntryId, String postedBy, Instant postedAt,
            List<DistributionSettlement> settlements, long revision
    ) {
        this.tenantId = SyirkahPartyTerms.requireText(tenantId, "tenantId");
        this.ledgerId = SyirkahPartyTerms.requireText(ledgerId, "ledgerId");
        this.obligationId = SyirkahPartyTerms.requireText(obligationId, "obligationId");
        this.closeCycleId = SyirkahPartyTerms.requireText(closeCycleId, "closeCycleId");
        this.calculationId = SyirkahPartyTerms.requireText(calculationId, "calculationId");
        this.calculationHash = SyirkahPartyTerms.requireText(calculationHash, "calculationHash");
        if (!this.calculationHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("calculationHash must be SHA-256 hex");
        this.contractId = SyirkahPartyTerms.requireText(contractId, "contractId");
        if (contractVersion < 1) throw new IllegalArgumentException("contractVersion must be positive");
        this.contractVersion = contractVersion;
        this.partyId = SyirkahPartyTerms.requireText(partyId, "partyId");
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) throw new IllegalArgumentException("distribution obligation amount must be greater than zero");
        this.currencyCode = SyirkahContractVersion.normalizeCurrency(currencyCode);
        SyirkahContractVersion.validateCurrencyScale(currencyScale);
        try {
            this.amount = amount.setScale(currencyScale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("distribution amount exceeds currency precision", ex);
        }
        this.currencyScale = currencyScale;
        this.preparedBy = SyirkahPartyTerms.requireText(preparedBy, "preparedBy");
        this.preparedAt = Objects.requireNonNull(preparedAt, "preparedAt must not be null");
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.journalEntryId = journalEntryId;
        this.postedBy = postedBy;
        this.postedAt = postedAt;
        this.settlements = List.copyOf(Objects.requireNonNull(settlements, "settlements must not be null"));
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
        this.revision = revision;
        validateStateShape();
    }

    public static PartnerDistributionObligation prepare(
            String tenantId, String ledgerId, String obligationId, String closeCycleId,
            String calculationId, String calculationHash, String contractId, int contractVersion,
            String partyId, BigDecimal amount, String currencyCode, int currencyScale,
            String preparedBy, Instant preparedAt
    ) {
        return new PartnerDistributionObligation(tenantId, ledgerId, obligationId, closeCycleId,
                calculationId, calculationHash, contractId, contractVersion, partyId, amount,
                currencyCode, currencyScale, preparedBy, preparedAt, null, null,
                null, null, null, List.of(), 0);
    }

    public PartnerDistributionObligation approve(String checker, Instant at, long expectedRevision) {
        requireRevision(expectedRevision);
        if (status() != Status.PREPARED) throw new IllegalStateException("Only PREPARED obligations can be approved");
        String normalizedChecker = SyirkahPartyTerms.requireText(checker, "checker");
        if (normalizedChecker.equals(preparedBy)) throw new IllegalArgumentException("Maker and checker must be different people");
        return copy(normalizedChecker, Objects.requireNonNull(at, "approvedAt must not be null"), null, null, null, settlements, revision + 1);
    }

    public PartnerDistributionObligation markPosted(String postedJournalEntryId, String poster, Instant at, long expectedRevision) {
        requireRevision(expectedRevision);
        if (status() != Status.APPROVED) throw new IllegalStateException("Only APPROVED obligations can be posted");
        return copy(approvedBy, approvedAt, SyirkahPartyTerms.requireText(postedJournalEntryId, "journalEntryId"),
                SyirkahPartyTerms.requireText(poster, "postedBy"), Objects.requireNonNull(at, "postedAt must not be null"), settlements, revision + 1);
    }

    public PartnerDistributionObligation recordSettlement(DistributionSettlement settlement, long expectedRevision) {
        Objects.requireNonNull(settlement, "settlement must not be null");
        Optional<DistributionSettlement> existing = settlements.stream().filter(s -> s.settlementId().equals(settlement.settlementId())).findFirst();
        if (existing.isPresent()) {
            if (existing.get().equals(settlement)) return this; // exact retry is idempotent even if the caller has a stale revision
            throw new IllegalArgumentException("Settlement ID was already used with different content: " + settlement.settlementId());
        }
        requireRevision(expectedRevision);
        if (journalEntryId == null) throw new IllegalStateException("Settlement is not allowed before the obligation is posted");
        if (!currencyCode.equals(settlement.currencyCode()) || currencyScale != settlement.currencyScale()) {
            throw new IllegalArgumentException("Settlement currency/precision does not match obligation");
        }
        BigDecimal newTotal = settledAmount().add(settlement.amount());
        if (newTotal.compareTo(amount) > 0) throw new IllegalArgumentException("Settlement exceeds obligation outstanding amount");
        List<DistributionSettlement> updated = new java.util.ArrayList<>(settlements);
        updated.add(settlement);
        return copy(approvedBy, approvedAt, journalEntryId, postedBy, postedAt, updated, revision + 1);
    }

    private PartnerDistributionObligation copy(String newApprovedBy, Instant newApprovedAt,
            String newJournalEntryId, String newPostedBy, Instant newPostedAt,
            List<DistributionSettlement> newSettlements, long newRevision) {
        return new PartnerDistributionObligation(tenantId, ledgerId, obligationId, closeCycleId,
                calculationId, calculationHash, contractId, contractVersion, partyId, amount,
                currencyCode, currencyScale, preparedBy, preparedAt, newApprovedBy, newApprovedAt,
                newJournalEntryId, newPostedBy, newPostedAt, newSettlements, newRevision);
    }

    private void requireRevision(long expectedRevision) {
        if (expectedRevision != revision) throw new ConcurrentModificationException("Obligation revision changed: expected " + expectedRevision + ", actual " + revision);
    }

    private void validateStateShape() {
        if ((approvedBy == null) != (approvedAt == null)) throw new IllegalArgumentException("Approval actor/time must be supplied together");
        if ((journalEntryId == null) != (postedBy == null) || (journalEntryId == null) != (postedAt == null)) {
            throw new IllegalArgumentException("Posting journal/actor/time must be supplied together");
        }
        if (approvedBy != null && approvedBy.equals(preparedBy)) throw new IllegalArgumentException("Maker and checker must be different people");
        if (approvedBy != null && approvedAt.isBefore(preparedAt)) throw new IllegalArgumentException("Approval cannot precede preparation");
        if (journalEntryId != null && approvedBy == null) throw new IllegalArgumentException("Posted obligation must have an approval");
        if (journalEntryId != null && postedAt.isBefore(approvedAt)) throw new IllegalArgumentException("Posting cannot precede approval");
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (DistributionSettlement settlement : settlements) {
            if (!ids.add(settlement.settlementId())) throw new IllegalArgumentException("Duplicate settlement ID: " + settlement.settlementId());
            if (!currencyCode.equals(settlement.currencyCode()) || currencyScale != settlement.currencyScale()) throw new IllegalArgumentException("Settlement currency/precision mismatch");
            if (postedAt != null && settlement.recordedAt().isBefore(postedAt)) throw new IllegalArgumentException("Settlement cannot precede journal posting");
        }
        if (settledAmount().compareTo(amount) > 0) throw new IllegalArgumentException("Total settlements exceed obligation amount");
        if (!settlements.isEmpty() && journalEntryId == null) throw new IllegalArgumentException("Settlement records require a posted obligation");
    }

    public Status status() {
        if (journalEntryId == null) return approvedBy == null ? Status.PREPARED : Status.APPROVED;
        BigDecimal settled = settledAmount();
        if (settled.compareTo(amount) == 0) return Status.SETTLED;
        return settled.signum() > 0 ? Status.PARTIALLY_SETTLED : Status.POSTED;
    }

    public BigDecimal settledAmount() {
        return settlements.stream().map(DistributionSettlement::amount).reduce(BigDecimal.ZERO.setScale(currencyScale), BigDecimal::add);
    }

    public BigDecimal outstandingAmount() { return amount.subtract(settledAmount()).setScale(currencyScale, RoundingMode.UNNECESSARY); }
    public String postingSourceType() { return POSTING_SOURCE_TYPE; }
    public String postingSourceId() { return obligationId; }
    public String tenantId() { return tenantId; }
    public String ledgerId() { return ledgerId; }
    public String obligationId() { return obligationId; }
    public String closeCycleId() { return closeCycleId; }
    public String calculationId() { return calculationId; }
    public String calculationHash() { return calculationHash; }
    public String contractId() { return contractId; }
    public int contractVersion() { return contractVersion; }
    public String partyId() { return partyId; }
    public BigDecimal amount() { return amount; }
    public String currencyCode() { return currencyCode; }
    public int currencyScale() { return currencyScale; }
    public String preparedBy() { return preparedBy; }
    public Instant preparedAt() { return preparedAt; }
    public String approvedBy() { return approvedBy; }
    public Instant approvedAt() { return approvedAt; }
    public String journalEntryId() { return journalEntryId; }
    public String postedBy() { return postedBy; }
    public Instant postedAt() { return postedAt; }
    public List<DistributionSettlement> settlements() { return settlements; }
    public long revision() { return revision; }
}
