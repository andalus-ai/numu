package tech.kayys.numou.asset.domain.warranty;

import tech.kayys.numou.asset.domain.event.warranty.WarrantyClaimApproved;
import tech.kayys.numou.asset.domain.event.warranty.WarrantyClaimCompleted;
import tech.kayys.numou.asset.domain.event.warranty.WarrantyClaimFiled;
import tech.kayys.numou.asset.domain.event.warranty.WarrantyClaimRejected;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Warranty claim aggregate (ASSET-23 §§14-15). The optional workOrderId is a reference
 * only: claim lifecycle stays independent from the maintenance WO lifecycle.
 */
public final class WarrantyClaim extends AbstractAggregateRoot<WarrantyClaimId> {

    private static final long serialVersionUID = 1L;

    private String tenantId;
    private AssetWarrantyId warrantyId;
    private UUID assetId;
    private String claimNumber;
    private WarrantyClaimStatus status;
    private String description;
    private String submittedBy;
    private Instant submittedAt;
    private Instant approvedAt;
    private Instant rejectedAt;
    private Instant completedAt;
    private UUID workOrderId;

    private WarrantyClaim() { super(); }

    private WarrantyClaim(WarrantyClaimId id, String tenantId, AssetWarrantyId warrantyId, UUID assetId, String claimNumber) {
        super(id);
        this.tenantId = requireText(tenantId, "tenantId");
        this.warrantyId = Objects.requireNonNull(warrantyId, "warrantyId cannot be null");
        this.assetId = Objects.requireNonNull(assetId, "assetId cannot be null");
        this.claimNumber = requireText(claimNumber, "claimNumber");
        this.status = WarrantyClaimStatus.DRAFT;
    }

    public static WarrantyClaim file(WarrantyClaimId id, String tenantId, AssetWarrantyId warrantyId, UUID assetId,
                                     String claimNumber, String description, String submittedBy, DomainClock clock) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(clock, "clock cannot be null");
        WarrantyClaim claim = new WarrantyClaim(id, tenantId, warrantyId, assetId, claimNumber);
        claim.description = description;
        claim.submittedBy = submittedBy;
        claim.setCreatedAt(clock.now());
        claim.setUpdatedAt(clock.now());
        claim.raise(new WarrantyClaimFiled(UUID.randomUUID(), clock.now(), id.value(), warrantyId.value(), claimNumber));
        return claim;
    }

    public static WarrantyClaim reconstitute(WarrantyClaimId id, String tenantId, AssetWarrantyId warrantyId,
                                             UUID assetId, String claimNumber, WarrantyClaimStatus status,
                                             String description, String submittedBy, Instant submittedAt,
                                             Instant approvedAt, Instant rejectedAt, Instant completedAt,
                                             UUID workOrderId) {
        WarrantyClaim claim = new WarrantyClaim(id, tenantId, warrantyId, assetId, claimNumber);
        claim.status = Objects.requireNonNull(status, "status cannot be null");
        claim.description = description;
        claim.submittedBy = submittedBy;
        claim.submittedAt = submittedAt;
        claim.approvedAt = approvedAt;
        claim.rejectedAt = rejectedAt;
        claim.completedAt = completedAt;
        claim.workOrderId = workOrderId;
        return claim;
    }

    public void submit(DomainClock clock) {
        requireStatus(WarrantyClaimStatus.DRAFT, "submit");
        status = WarrantyClaimStatus.SUBMITTED;
        submittedAt = clock.now();
        touch(clock);
    }

    public void startReview(DomainClock clock) {
        requireStatus(WarrantyClaimStatus.SUBMITTED, "start review");
        status = WarrantyClaimStatus.UNDER_REVIEW;
        touch(clock);
    }

    public void approve(UUID workOrderId, DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (status != WarrantyClaimStatus.SUBMITTED && status != WarrantyClaimStatus.UNDER_REVIEW) {
            throw new InvalidStateException("Claim cannot be approved from " + status);
        }
        status = WarrantyClaimStatus.APPROVED;
        approvedAt = clock.now();
        this.workOrderId = workOrderId;
        touch(clock);
        raise(new WarrantyClaimApproved(UUID.randomUUID(), clock.now(), id.value(), workOrderId));
    }

    public void markInService(DomainClock clock) {
        requireStatus(WarrantyClaimStatus.APPROVED, "mark in-service");
        status = WarrantyClaimStatus.IN_SERVICE;
        touch(clock);
    }

    public void reject(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (status != WarrantyClaimStatus.SUBMITTED && status != WarrantyClaimStatus.UNDER_REVIEW) {
            throw new InvalidStateException("Claim cannot be rejected from " + status);
        }
        status = WarrantyClaimStatus.REJECTED;
        rejectedAt = clock.now();
        touch(clock);
        raise(new WarrantyClaimRejected(UUID.randomUUID(), clock.now(), id.value()));
    }

    public void complete(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (status != WarrantyClaimStatus.APPROVED && status != WarrantyClaimStatus.IN_SERVICE) {
            throw new InvalidStateException("Claim cannot be completed from " + status);
        }
        status = WarrantyClaimStatus.COMPLETED;
        completedAt = clock.now();
        touch(clock);
        raise(new WarrantyClaimCompleted(UUID.randomUUID(), clock.now(), id.value()));
    }

    public void cancel(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (status == WarrantyClaimStatus.COMPLETED || status == WarrantyClaimStatus.CANCELLED) {
            throw new InvalidStateException("Claim is already closed: " + status);
        }
        status = WarrantyClaimStatus.CANCELLED;
        touch(clock);
    }

    private void requireStatus(WarrantyClaimStatus expected, String operation) {
        Objects.requireNonNull(expected, "expected cannot be null");
        if (status != expected) {
            throw new InvalidStateException("Claim cannot " + operation + " from " + status);
        }
    }

    private void touch(DomainClock clock) {
        setUpdatedAt(clock.now());
        incrementVersion();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new BusinessRuleViolation(field + " cannot be blank");
        }
        return normalized;
    }

    public String tenantId() { return tenantId; }
    public AssetWarrantyId warrantyId() { return warrantyId; }
    public UUID assetId() { return assetId; }
    public String claimNumber() { return claimNumber; }
    public WarrantyClaimStatus status() { return status; }
    public String description() { return description; }
    public String submittedBy() { return submittedBy; }
    public Instant submittedAt() { return submittedAt; }
    public Instant approvedAt() { return approvedAt; }
    public Instant rejectedAt() { return rejectedAt; }
    public Instant completedAt() { return completedAt; }
    public UUID workOrderId() { return workOrderId; }
}
