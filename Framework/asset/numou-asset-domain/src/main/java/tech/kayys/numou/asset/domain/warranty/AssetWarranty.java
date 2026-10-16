package tech.kayys.numou.asset.domain.warranty;

import tech.kayys.numou.asset.domain.event.warranty.WarrantyActivated;
import tech.kayys.numou.asset.domain.event.warranty.WarrantyCancelled;
import tech.kayys.numou.asset.domain.event.warranty.WarrantyCoverageAdded;
import tech.kayys.numou.asset.domain.event.warranty.WarrantyExpired;
import tech.kayys.numou.asset.domain.event.warranty.WarrantyRegistered;
import tech.kayys.numou.asset.domain.meter.MeterType;
import tech.kayys.numou.asset.domain.meter.MeterUnit;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Warranty aggregate: owns coverage, validity and claim-facing rules (ASSET-23).
 *
 * <p>The asset never stores warranty fields: original + extended + overlapping +
 * component-specific + historical warranties all live here.</p>
 */
public final class AssetWarranty extends AbstractAggregateRoot<AssetWarrantyId> {

    private static final long serialVersionUID = 1L;

    private String tenantId;
    private UUID assetId;
    private String warrantyNumber;
    private String providerId;
    private String providerName;
    private WarrantyType type;
    private WarrantyStatus status;
    private WarrantyExpiryRule expiryRule;
    private Instant startsAt;
    private Instant expiresAt;
    private MeterType meterType;
    private BigDecimal meterLimit;
    private MeterUnit meterUnit;
    private final List<WarrantyCoverage> coverages = new ArrayList<>();
    private final List<WarrantyExclusion> exclusions = new ArrayList<>();

    private AssetWarranty() { super(); }

    private AssetWarranty(AssetWarrantyId id, String tenantId, UUID assetId, String warrantyNumber) {
        super(id);
        this.tenantId = requireText(tenantId, "tenantId");
        this.assetId = Objects.requireNonNull(assetId, "assetId cannot be null");
        this.warrantyNumber = requireText(warrantyNumber, "warrantyNumber");
        this.status = WarrantyStatus.DRAFT;
    }

    public static AssetWarranty register(AssetWarrantyId id, String tenantId, UUID assetId, String warrantyNumber,
                                         String providerId, String providerName, WarrantyType type,
                                         WarrantyExpiryRule expiryRule, Instant startsAt, Instant expiresAt,
                                         MeterType meterType, BigDecimal meterLimit, MeterUnit meterUnit,
                                         List<WarrantyCoverage> coverages, List<WarrantyExclusion> exclusions,
                                         DomainClock clock) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(clock, "clock cannot be null");
        AssetWarranty warranty = new AssetWarranty(id, tenantId, assetId, warrantyNumber);
        warranty.providerId = providerId;
        warranty.providerName = providerName;
        warranty.type = Objects.requireNonNull(type, "type cannot be null");
        warranty.expiryRule = Objects.requireNonNull(expiryRule, "expiryRule cannot be null");
        warranty.startsAt = Objects.requireNonNull(startsAt, "startsAt cannot be null");
        warranty.expiresAt = expiresAt;
        warranty.meterType = meterType;
        warranty.meterLimit = meterLimit;
        warranty.meterUnit = meterUnit;
        validateValidity(warranty);
        if (coverages != null) warranty.coverages.addAll(coverages);
        if (exclusions != null) warranty.exclusions.addAll(exclusions);
        warranty.setCreatedAt(clock.now());
        warranty.setUpdatedAt(clock.now());
        warranty.raise(new WarrantyRegistered(UUID.randomUUID(), clock.now(), id.value(), assetId, warrantyNumber));
        return warranty;
    }

    public static AssetWarranty reconstitute(AssetWarrantyId id, String tenantId, UUID assetId, String warrantyNumber,
                                             String providerId, String providerName, WarrantyType type,
                                             WarrantyStatus status, WarrantyExpiryRule expiryRule,
                                             Instant startsAt, Instant expiresAt, MeterType meterType,
                                             BigDecimal meterLimit, MeterUnit meterUnit,
                                             List<WarrantyCoverage> coverages, List<WarrantyExclusion> exclusions) {
        AssetWarranty warranty = new AssetWarranty(id, tenantId, assetId, warrantyNumber);
        warranty.providerId = providerId;
        warranty.providerName = providerName;
        warranty.type = type;
        warranty.status = Objects.requireNonNull(status, "status cannot be null");
        warranty.expiryRule = expiryRule;
        warranty.startsAt = startsAt;
        warranty.expiresAt = expiresAt;
        warranty.meterType = meterType;
        warranty.meterLimit = meterLimit;
        warranty.meterUnit = meterUnit;
        if (coverages != null) warranty.coverages.addAll(coverages);
        if (exclusions != null) warranty.exclusions.addAll(exclusions);
        return warranty;
    }

    private static void validateValidity(AssetWarranty warranty) {
        boolean needsDate = warranty.expiryRule == WarrantyExpiryRule.DATE
                || warranty.expiryRule == WarrantyExpiryRule.DATE_OR_METER
                || warranty.expiryRule == WarrantyExpiryRule.DATE_AND_METER;
        boolean needsMeter = warranty.expiryRule == WarrantyExpiryRule.METER
                || warranty.expiryRule == WarrantyExpiryRule.DATE_OR_METER
                || warranty.expiryRule == WarrantyExpiryRule.DATE_AND_METER;
        if (needsDate && warranty.expiresAt == null) {
            throw new BusinessRuleViolation("expiresAt is required for expiry rule " + warranty.expiryRule);
        }
        if (needsDate && warranty.expiresAt != null && !warranty.expiresAt.isAfter(warranty.startsAt)) {
            throw new BusinessRuleViolation("expiresAt must be after startsAt");
        }
        if (needsMeter && (warranty.meterLimit == null || warranty.meterLimit.signum() <= 0)) {
            throw new BusinessRuleViolation("meterLimit must be positive for expiry rule " + warranty.expiryRule);
        }
        if (needsMeter && warranty.meterType == null) {
            throw new BusinessRuleViolation("meterType is required for expiry rule " + warranty.expiryRule);
        }
    }

    public void addCoverage(WarrantyCoverage coverage, DomainClock clock) {
        Objects.requireNonNull(coverage, "coverage cannot be null");
        Objects.requireNonNull(clock, "clock cannot be null");
        coverages.add(coverage);
        touch(clock);
        raise(new WarrantyCoverageAdded(UUID.randomUUID(), clock.now(), id.value(), coverage.id()));
    }

    public void activate(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (status != WarrantyStatus.DRAFT && status != WarrantyStatus.SUSPENDED) {
            throw new InvalidStateException("Warranty cannot transition from " + status + " to ACTIVE");
        }
        status = WarrantyStatus.ACTIVE;
        touch(clock);
        raise(new WarrantyActivated(UUID.randomUUID(), clock.now(), id.value()));
    }

    public void suspend(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (status != WarrantyStatus.ACTIVE) {
            throw new InvalidStateException("Only ACTIVE warranties can be suspended (was " + status + ")");
        }
        status = WarrantyStatus.SUSPENDED;
        touch(clock);
    }

    public void expire(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (status == WarrantyStatus.EXPIRED || status == WarrantyStatus.CANCELLED || status == WarrantyStatus.EXHAUSTED) {
            throw new InvalidStateException("Warranty is already closed: " + status);
        }
        status = WarrantyStatus.EXPIRED;
        touch(clock);
        raise(new WarrantyExpired(UUID.randomUUID(), clock.now(), id.value()));
    }

    public void exhaust(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        status = WarrantyStatus.EXHAUSTED;
        touch(clock);
    }

    public void cancel(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (status == WarrantyStatus.EXPIRED || status == WarrantyStatus.CANCELLED) {
            throw new InvalidStateException("Warranty is already closed: " + status);
        }
        status = WarrantyStatus.CANCELLED;
        touch(clock);
        raise(new WarrantyCancelled(UUID.randomUUID(), clock.now(), id.value()));
    }

    /** True when the date/meter limits end coverage at the given point-in-time/reading. */
    public boolean isExpiredAt(Instant now, BigDecimal meterValue) {
        Objects.requireNonNull(now, "now cannot be null");
        boolean dateHit = expiresAt != null && !now.isBefore(expiresAt);
        boolean meterHit = meterLimit != null && meterValue != null && meterValue.compareTo(meterLimit) >= 0;
        return switch (expiryRule) {
            case DATE -> dateHit;
            case METER -> meterHit;
            case DATE_OR_METER -> dateHit || meterHit;
            case DATE_AND_METER -> dateHit && meterHit;
        };
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
    public UUID assetId() { return assetId; }
    public String warrantyNumber() { return warrantyNumber; }
    public String providerId() { return providerId; }
    public String providerName() { return providerName; }
    public WarrantyType type() { return type; }
    public WarrantyStatus status() { return status; }
    public WarrantyExpiryRule expiryRule() { return expiryRule; }
    public Instant startsAt() { return startsAt; }
    public Instant expiresAt() { return expiresAt; }
    public MeterType meterType() { return meterType; }
    public BigDecimal meterLimit() { return meterLimit; }
    public MeterUnit meterUnit() { return meterUnit; }
    public List<WarrantyCoverage> coverages() { return Collections.unmodifiableList(coverages); }
    public List<WarrantyExclusion> exclusions() { return Collections.unmodifiableList(exclusions); }
}
