package tech.kayys.syirkah.asset.domain.availability;

import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A tenant-scoped, time-bounded (or open-ended) availability window for an
 * asset (ASSET-26 §6-7).
 *
 * <p>Availability is <em>temporal business information</em> and never lives on
 * the {@code Asset} aggregate (§3). A period is an immutable fact: a correction
 * is a new period, not an in-place mutation.</p>
 *
 * <p>{@code endsAt} may be {@code null} to express an open-ended state such as
 * "unavailable since 10:00 until further notice". Only one open-ended period
 * should normally be active per asset; that rule is enforced at the application
 * layer because it needs cross-period knowledge.</p>
 */
public final class AssetAvailabilityPeriod {

    private final AssetAvailabilityPeriodId id;
    private final String tenantId;
    private final UUID assetId;
    private final Instant startsAt;
    private final Instant endsAt;
    private final AssetAvailabilityType type;
    private final AssetAvailabilityReason reason;
    private final String referenceId;
    private final String notes;

    private AssetAvailabilityPeriod(
            AssetAvailabilityPeriodId id,
            String tenantId,
            UUID assetId,
            Instant startsAt,
            Instant endsAt,
            AssetAvailabilityType type,
            AssetAvailabilityReason reason,
            String referenceId,
            String notes) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        this.tenantId = tenantId;
        this.assetId = Objects.requireNonNull(assetId, "assetId cannot be null");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt cannot be null");
        if (endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new BusinessRuleViolation("Availability endsAt must be strictly after startsAt");
        }
        this.endsAt = endsAt;
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.reason = reason == null ? AssetAvailabilityReason.OTHER : reason;
        this.referenceId = referenceId;
        this.notes = notes;
    }

    /** Creates a new validated availability period. */
    public static AssetAvailabilityPeriod mark(
            AssetAvailabilityPeriodId id,
            String tenantId,
            UUID assetId,
            Instant startsAt,
            Instant endsAt,
            AssetAvailabilityType type,
            AssetAvailabilityReason reason,
            String referenceId,
            String notes) {
        return new AssetAvailabilityPeriod(
                id, tenantId, assetId, startsAt, endsAt, type, reason, referenceId, notes);
    }

    /** Rehydrates a period from persistence without re-validating business rules. */
    public static AssetAvailabilityPeriod of(
            AssetAvailabilityPeriodId id,
            String tenantId,
            UUID assetId,
            Instant startsAt,
            Instant endsAt,
            AssetAvailabilityType type,
            AssetAvailabilityReason reason,
            String referenceId,
            String notes) {
        return new AssetAvailabilityPeriod(
                id, tenantId, assetId, startsAt, endsAt, type, reason, referenceId, notes);
    }

    public boolean isOpen() {
        return endsAt == null;
    }

    public boolean isUnavailable() {
        return type == AssetAvailabilityType.UNAVAILABLE;
    }

    public boolean available() {
        return type == AssetAvailabilityType.AVAILABLE;
    }

    /** Returns {@code true} when {@code at} falls inside this window (inclusive start, exclusive end). */
    public boolean covers(Instant at) {
        Objects.requireNonNull(at, "at cannot be null");
        if (at.isBefore(startsAt)) {
            return false;
        }
        return endsAt == null || at.isBefore(endsAt);
    }

    /**
     * Half-open temporal overlap test: two windows conflict when
     * {@code start < otherEnd && otherStart < end}, treating a {@code null} end
     * as "infinitely far in the future" (§7, §acceptance "temporal overlap detection").
     */
    public static boolean overlaps(Instant startA, Instant endA, Instant startB, Instant endB) {
        Objects.requireNonNull(startA, "startA cannot be null");
        Objects.requireNonNull(startB, "startB cannot be null");
        Instant effectiveEndA = endA == null ? Instant.MAX : endA;
        Instant effectiveEndB = endB == null ? Instant.MAX : endB;
        return startA.isBefore(effectiveEndB) && startB.isBefore(effectiveEndA);
    }

    /** Returns {@code true} when this period overlaps the given window. */
    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return overlaps(this.startsAt, this.endsAt, otherStart, otherEnd);
    }

    public AssetAvailabilityPeriodId id() {
        return id;
    }

    public String tenantId() {
        return tenantId;
    }

    public UUID assetId() {
        return assetId;
    }

    public Instant startsAt() {
        return startsAt;
    }

    public Instant endsAt() {
        return endsAt;
    }

    public AssetAvailabilityType type() {
        return type;
    }

    public AssetAvailabilityReason reason() {
        return reason;
    }

    public String referenceId() {
        return referenceId;
    }

    public String notes() {
        return notes;
    }
}