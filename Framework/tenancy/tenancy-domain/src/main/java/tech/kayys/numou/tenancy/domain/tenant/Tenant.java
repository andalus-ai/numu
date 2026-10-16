package tech.kayys.syirkah.tenancy.domain.tenant;

import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

import tech.kayys.syirkah.foundation.domain.audit.AuditMeta;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.ref.OrganizationRef;
import tech.kayys.syirkah.tenancy.domain.tenant.event.TenantActivated;
import tech.kayys.syirkah.tenancy.domain.tenant.event.TenantCreated;
import tech.kayys.syirkah.tenancy.domain.tenant.event.TenantDeactivated;
import tech.kayys.syirkah.tenancy.domain.tenant.event.TenantOrganizationChanged;
import tech.kayys.syirkah.tenancy.domain.tenant.event.TenantRenamed;
import tech.kayys.syirkah.tenancy.domain.tenant.event.TenantSuspended;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant aggregate root — the fundamental multi-tenancy boundary of the platform.
 *
 * <p>Deliberately narrow: holds only identity, lifecycle, and organization reference.
 * Membership, Settings, and Provisioning are separate aggregates.
 *
 * <p>Does NOT contain: User, Employee, Worker, Address, Phone, Subscription, Role,
 * Permission, or any Organization detail fields.
 */
public final class Tenant extends AbstractAggregateRoot<TenantId> {

    private String code;
    private String name;
    private OrganizationRef organization;
    private TenantStatus status;
    private final AuditMeta audit;

    private Tenant(TenantId id, String code, String name,
                   OrganizationRef organization, TenantStatus status,
                   AuditMeta audit) {
        super(id);
        this.code         = requireText(code, "code");
        this.name         = requireText(name, "name");
        this.organization = organization;               // nullable — organization is optional
        this.status       = Objects.requireNonNull(status);
        this.audit        = Objects.requireNonNull(audit);
    }

    /**
     * Creates a new Tenant in PROVISIONING state.
     * Provisioning workflow (settings, features, access) must complete
     * before the tenant can be activated.
     */
    public static Tenant create(TenantId id, String code, String name,
                                OrganizationRef organization, AuditMeta audit) {
        var tenant = new Tenant(id, code, name, organization, TenantStatus.PROVISIONING, audit);
        tenant.raise(new TenantCreated(UUID.randomUUID(), Instant.now(), id, code));
        return tenant;
    }

    /**
     * Transitions PROVISIONING → ACTIVE or SUSPENDED → ACTIVE.
     */
    public void activate() {
        if (status != TenantStatus.PROVISIONING && status != TenantStatus.SUSPENDED) {
            throw new IllegalStateException("Tenant cannot be activated from " + status);
        }
        status = TenantStatus.ACTIVE;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new TenantActivated(UUID.randomUUID(), Instant.now(), id()));
    }

    /**
     * Transitions ACTIVE → SUSPENDED.
     */
    public void suspend() {
        if (status != TenantStatus.ACTIVE) {
            throw new IllegalStateException("Tenant cannot be suspended from " + status);
        }
        status = TenantStatus.SUSPENDED;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new TenantSuspended(UUID.randomUUID(), Instant.now(), id()));
    }

    /**
     * Transitions any non-INACTIVE state → INACTIVE (idempotent if already inactive).
     */
    public void deactivate() {
        if (status == TenantStatus.INACTIVE) {
            return;
        }
        status = TenantStatus.INACTIVE;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new TenantDeactivated(UUID.randomUUID(), Instant.now(), id()));
    }

    /** Renames the tenant (display name only, code is immutable). */
    public void rename(String name) {
        var previous = this.name;
        this.name = requireText(name, "name");
        incrementVersion();
        updatedAt = Instant.now();
        raise(new TenantRenamed(UUID.randomUUID(), Instant.now(), id(), previous, name));
    }

    /** Associates (or clears) the legal Organization for this tenant. */
    public void changeOrganization(OrganizationRef organization) {
        this.organization = organization;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new TenantOrganizationChanged(UUID.randomUUID(), Instant.now(), id(), organization));
    }

    // ── accessors ────────────────────────────────────────────────────────────

    public String code()             { return code; }
    public String name()             { return name; }
    public OrganizationRef organization() { return organization; }
    public TenantStatus status()     { return status; }
    public AuditMeta audit()         { return audit; }
    public boolean isActive()        { return status == TenantStatus.ACTIVE; }

    // ── private helpers ───────────────────────────────────────────────────────

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
