package tech.kayys.syirkah.organization.domain;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.organization.domain.event.OrganizationCreated;
import tech.kayys.syirkah.organization.domain.event.OrganizationDissolved;
import tech.kayys.syirkah.organization.domain.event.OrganizationReactivated;
import tech.kayys.syirkah.organization.domain.event.OrganizationSuspended;

import java.time.Instant;
import java.util.Objects;

/**
 * Organization aggregate — represents a legal entity or business unit within the platform.
 *
 * <p>Lifecycle: {@code ACTIVE} ↔ {@code SUSPENDED} → {@code DISSOLVED}.
 * <p>This is a standalone bounded context reusable by workforce, project, CRM, etc.
 */
public final class Organization extends AbstractAggregateRoot<OrganizationId> {

    private final TenantId tenantId;
    private String name;
    private String legalName;
    private String registrationNumber;
    private OrganizationStatus status;

    private Organization(OrganizationId id, TenantId tenantId, String name,
                          String legalName, String registrationNumber) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.legalName = legalName;
        this.registrationNumber = registrationNumber;
        this.status = OrganizationStatus.ACTIVE;
    }

    public static Organization create(OrganizationId id, TenantId tenantId, String name,
                                       String legalName, String registrationNumber) {
        Organization org = new Organization(id, tenantId, name, legalName, registrationNumber);
        org.raise(new OrganizationCreated(id, tenantId, name, legalName, registrationNumber));
        return org;
    }

    public void suspend() {
        if (status != OrganizationStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE organizations can be suspended");
        }
        status = OrganizationStatus.SUSPENDED;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new OrganizationSuspended(getId(), tenantId));
    }

    public void reactivate() {
        if (status != OrganizationStatus.SUSPENDED) {
            throw new IllegalStateException("Only SUSPENDED organizations can be reactivated");
        }
        status = OrganizationStatus.ACTIVE;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new OrganizationReactivated(getId(), tenantId));
    }

    public void dissolve() {
        if (status == OrganizationStatus.DISSOLVED) {
            return;
        }
        status = OrganizationStatus.DISSOLVED;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new OrganizationDissolved(getId(), tenantId));
    }

    public void rename(String newName) {
        Objects.requireNonNull(newName, "newName must not be null");
        if (newName.isBlank()) {
            throw new IllegalArgumentException("Organization name must not be blank");
        }
        this.name = newName;
        incrementVersion();
        updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getLegalName() { return legalName; }
    public String getRegistrationNumber() { return registrationNumber; }
    public OrganizationStatus getStatus() { return status; }
    public boolean isActive() { return status == OrganizationStatus.ACTIVE; }
}
