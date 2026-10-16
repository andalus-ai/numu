package tech.kayys.syirkah.construction.domain.boq;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable commercial snapshot for one BOQ revision.
 *
 * This value deliberately stores copied scalar values rather than live BoqItem
 * references. Persist snapshots separately from the mutable Boq aggregate.
 */
public record BoqRevisionSnapshot(
        UUID boqId,
        UUID projectId,
        int revisionNumber,
        String boqName,
        RevisionStatus status,
        List<ItemSnapshot> items,
        String calculationPolicyVersion,
        UUID createdBy,
        Instant createdAt,
        UUID submittedBy,
        Instant submittedAt,
        UUID approvedBy,
        Instant approvedAt
) {
    public BoqRevisionSnapshot {
        Objects.requireNonNull(boqId, "BOQ id cannot be null");
        Objects.requireNonNull(projectId, "Project id cannot be null");
        if (revisionNumber < 1) throw new IllegalArgumentException("Revision number must be >= 1");
        if (boqName == null || boqName.isBlank()) throw new IllegalArgumentException("BOQ name cannot be blank");
        Objects.requireNonNull(status, "Revision status cannot be null");
        items = List.copyOf(Objects.requireNonNull(items, "Items cannot be null"));
        if (calculationPolicyVersion == null || calculationPolicyVersion.isBlank()) {
            throw new IllegalArgumentException("Calculation policy version cannot be blank");
        }
        Objects.requireNonNull(createdAt, "Created timestamp cannot be null");
        if (status == RevisionStatus.UNDER_REVIEW && (submittedBy == null || submittedAt == null)) {
            throw new IllegalArgumentException("Submitted revision requires submitter and timestamp");
        }
        if (status == RevisionStatus.APPROVED &&
                (submittedBy == null || submittedAt == null || approvedBy == null || approvedAt == null)) {
            throw new IllegalArgumentException("Approved revision requires submission and approval metadata");
        }
    }

    /** Capture current BOQ and item values into an independent draft snapshot. */
    public static BoqRevisionSnapshot captureDraft(
            Boq boq,
            List<BoqItem> boqItems,
            BoqCalculationPolicy calculationPolicy,
            UUID createdBy,
            Instant now
    ) {
        Objects.requireNonNull(boq, "BOQ cannot be null");
        Objects.requireNonNull(boqItems, "BOQ items cannot be null");
        Objects.requireNonNull(calculationPolicy, "Calculation policy cannot be null");
        List<ItemSnapshot> copiedItems = boqItems.stream().map(item -> {
            BigDecimal amount = calculationPolicy.calculateLineAmount(item.quantity(), item.rate());
            return new ItemSnapshot(
                    item.id().value(), item.wbsNodeId(), item.itemCode(), item.description(),
                    item.itemType(), item.quantity().value(), item.quantity().unit(),
                    item.rate().amount(), item.rate().unit(), item.rate().currency().getCurrencyCode(), amount
            );
        }).toList();
        return draft(boq.id().value(), boq.projectId(), boq.currentRevision(), boq.name(),
                copiedItems, calculationPolicy.version(), createdBy, now);
    }

    public static BoqRevisionSnapshot draft(
            UUID boqId,
            UUID projectId,
            int revisionNumber,
            String boqName,
            List<ItemSnapshot> items,
            String calculationPolicyVersion,
            UUID createdBy,
            Instant now
    ) {
        return new BoqRevisionSnapshot(boqId, projectId, revisionNumber, boqName,
                RevisionStatus.DRAFT, items, calculationPolicyVersion, createdBy, now,
                null, null, null, null);
    }

    public BoqRevisionSnapshot submit(UUID actorId, Instant now) {
        requireStatus(RevisionStatus.DRAFT);
        return new BoqRevisionSnapshot(boqId, projectId, revisionNumber, boqName,
                RevisionStatus.UNDER_REVIEW, items, calculationPolicyVersion, createdBy, createdAt,
                Objects.requireNonNull(actorId, "Submitter cannot be null"),
                Objects.requireNonNull(now, "Submission time cannot be null"), null, null);
    }

    public BoqRevisionSnapshot approve(UUID actorId, Instant now) {
        requireStatus(RevisionStatus.UNDER_REVIEW);
        Objects.requireNonNull(actorId, "Approver cannot be null");
        if (actorId.equals(submittedBy)) {
            throw new IllegalArgumentException("The submitting actor cannot approve the same BOQ revision");
        }
        return new BoqRevisionSnapshot(boqId, projectId, revisionNumber, boqName,
                RevisionStatus.APPROVED, items, calculationPolicyVersion, createdBy, createdAt,
                submittedBy, submittedAt,
                actorId, Objects.requireNonNull(now, "Approval time cannot be null"));
    }

    public BigDecimal totalAmount() {
        return items.stream().map(ItemSnapshot::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void requireStatus(RevisionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected revision status " + expected + " but was " + status);
        }
    }

    public enum RevisionStatus { DRAFT, UNDER_REVIEW, APPROVED, SUPERSEDED }

    public record ItemSnapshot(
            UUID itemId,
            UUID wbsNodeId,
            String itemCode,
            String description,
            BoqItemType itemType,
            BigDecimal quantity,
            String quantityUnit,
            BigDecimal rate,
            String rateUnit,
            String currencyCode,
            BigDecimal amount
    ) {
        public ItemSnapshot {
            Objects.requireNonNull(itemId, "Item id cannot be null");
            Objects.requireNonNull(wbsNodeId, "WBS node id cannot be null");
            if (itemCode == null || itemCode.isBlank()) throw new IllegalArgumentException("Item code cannot be blank");
            if (description == null || description.isBlank()) throw new IllegalArgumentException("Description cannot be blank");
            Objects.requireNonNull(itemType, "Item type cannot be null");
            Objects.requireNonNull(quantity, "Quantity cannot be null");
            if (quantity.signum() < 0) throw new IllegalArgumentException("Quantity cannot be negative");
            if (quantityUnit == null || quantityUnit.isBlank()) throw new IllegalArgumentException("Quantity unit cannot be blank");
            Objects.requireNonNull(rate, "Rate cannot be null");
            if (rate.signum() < 0) throw new IllegalArgumentException("Rate cannot be negative");
            if (rateUnit == null || rateUnit.isBlank()) throw new IllegalArgumentException("Rate unit cannot be blank");
            if (currencyCode == null || !currencyCode.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException("Currency code must be a three-letter uppercase code");
            }
            Objects.requireNonNull(amount, "Amount cannot be null");
            if (amount.signum() < 0) throw new IllegalArgumentException("Amount cannot be negative");
        }
    }
}
