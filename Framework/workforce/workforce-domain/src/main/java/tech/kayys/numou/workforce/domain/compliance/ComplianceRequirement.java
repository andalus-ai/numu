package tech.kayys.numou.workforce.domain.compliance;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.compliance.event.ComplianceRequirementCreated;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Aggregate root representing a single compliance requirement that governs
 * what workers or employments must satisfy (e.g. a regulation, a policy,
 * a mandatory certification).
 *
 * <p>Lifecycle transitions:
 * <pre>
 *   ACTIVE → INACTIVE   (deactivate)
 *   ACTIVE → SUPERSEDED (supersede)
 * </pre>
 */
public class ComplianceRequirement extends AbstractAggregateRoot<ComplianceRequirementId> {

    private final TenantId tenantId;
    private final String code;
    private final String name;
    private final String description;
    private final ComplianceRequirementType type;
    private ComplianceRequirementStatus status;
    private final LocalDate effectiveFrom;
    private LocalDate effectiveTo; // nullable – open-ended when null

    // -------------------------------------------------------------------------
    // Constructor (private – use factory)
    // -------------------------------------------------------------------------

    private ComplianceRequirement(
            ComplianceRequirementId id,
            TenantId tenantId,
            String code,
            String name,
            String description,
            ComplianceRequirementType type,
            ComplianceRequirementStatus status,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        super(id);
        this.tenantId      = Objects.requireNonNull(tenantId,      "tenantId must not be null");
        this.code          = Objects.requireNonNull(code,          "code must not be null");
        this.name          = Objects.requireNonNull(name,          "name must not be null");
        this.description   = Objects.requireNonNull(description,   "description must not be null");
        this.type          = Objects.requireNonNull(type,          "type must not be null");
        this.status        = Objects.requireNonNull(status,        "status must not be null");
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        this.effectiveTo   = effectiveTo; // nullable
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Create a new active compliance requirement and raise a
     * {@link ComplianceRequirementCreated} domain event.
     */
    public static ComplianceRequirement create(
            TenantId tenantId,
            String code,
            String name,
            String description,
            ComplianceRequirementType type,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        ComplianceRequirementId id = ComplianceRequirementId.generate();

        ComplianceRequirement requirement = new ComplianceRequirement(
                id, tenantId, code, name, description, type,
                ComplianceRequirementStatus.ACTIVE, effectiveFrom, effectiveTo);

        requirement.registerEvent(new ComplianceRequirementCreated(id, tenantId, code, type));
        return requirement;
    }

    /**
     * Reconstitute an existing requirement from persistence (no events raised).
     */
    public static ComplianceRequirement reconstitute(
            ComplianceRequirementId id,
            TenantId tenantId,
            String code,
            String name,
            String description,
            ComplianceRequirementType type,
            ComplianceRequirementStatus status,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        return new ComplianceRequirement(
                id, tenantId, code, name, description, type, status, effectiveFrom, effectiveTo);
    }

    // -------------------------------------------------------------------------
    // Behaviour
    // -------------------------------------------------------------------------

    /**
     * Deactivate this requirement. Only ACTIVE requirements may be deactivated.
     *
     * @throws IllegalStateException if the requirement is not ACTIVE.
     */
    public void deactivate() {
        if (this.status != ComplianceRequirementStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Cannot deactivate requirement in status: " + this.status);
        }
        this.status = ComplianceRequirementStatus.INACTIVE;
    }

    /**
     * Mark this requirement as superseded (replaced by a newer version).
     * Only ACTIVE requirements may be superseded.
     *
     * @throws IllegalStateException if the requirement is not ACTIVE.
     */
    public void supersede() {
        if (this.status != ComplianceRequirementStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Cannot supersede requirement in status: " + this.status);
        }
        this.status = ComplianceRequirementStatus.SUPERSEDED;
    }

    /**
     * Returns {@code true} when this requirement is in force on the given date,
     * i.e. ACTIVE and the date falls within [effectiveFrom, effectiveTo].
     *
     * @param date the date to check; must not be null.
     */
    public boolean isEffectiveOn(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        if (this.status != ComplianceRequirementStatus.ACTIVE) {
            return false;
        }
        boolean afterStart = !date.isBefore(effectiveFrom);
        boolean beforeEnd  = (effectiveTo == null) || !date.isAfter(effectiveTo);
        return afterStart && beforeEnd;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public TenantId getTenantId()                     { return tenantId; }
    public String getCode()                            { return code; }
    public String getName()                            { return name; }
    public String getDescription()                     { return description; }
    public ComplianceRequirementType getType()         { return type; }
    public ComplianceRequirementStatus getStatus()     { return status; }
    public LocalDate getEffectiveFrom()                { return effectiveFrom; }
    public LocalDate getEffectiveTo()                  { return effectiveTo; }
}
