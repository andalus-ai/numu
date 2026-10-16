package tech.kayys.syirkah.organization.domain;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.organization.domain.event.OrganizationUnitCreated;
import tech.kayys.syirkah.organization.domain.event.OrganizationUnitDissolved;

import java.time.Instant;
import java.util.Objects;

/**
 * OrganizationUnit aggregate — a structural subdivision of an {@link Organization}
 * (e.g., department, division, team).
 *
 * <p>Units can be nested: each unit holds an optional reference to its parent
 * unit id, enabling hierarchical org charts.
 */
public final class OrganizationUnit extends AbstractAggregateRoot<OrganizationUnitId> {

    private final OrganizationId organizationId;
    private final OrganizationUnitId parentUnitId; // nullable — root unit has no parent
    private OrganizationUnitKind kind;
    private String name;
    private String code;
    private OrganizationUnitStatus status;

    private OrganizationUnit(OrganizationUnitId id, OrganizationId organizationId,
                              OrganizationUnitId parentUnitId, OrganizationUnitKind kind, String name, String code) {
        super(id);
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId must not be null");
        this.parentUnitId = parentUnitId; // nullable
        this.kind = kind != null ? kind : OrganizationUnitKind.DEPARTMENT;
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.code = code;
        this.status = OrganizationUnitStatus.ACTIVE;
    }

    public static OrganizationUnit create(OrganizationUnitId id, OrganizationId organizationId,
                                           OrganizationUnitId parentUnitId, String name, String code) {
        return create(id, organizationId, parentUnitId, OrganizationUnitKind.DEPARTMENT, name, code);
    }

    public static OrganizationUnit create(OrganizationUnitId id, OrganizationId organizationId,
                                           OrganizationUnitId parentUnitId, OrganizationUnitKind kind, String name, String code) {
        OrganizationUnit unit = new OrganizationUnit(id, organizationId, parentUnitId, kind, name, code);
        unit.raise(new OrganizationUnitCreated(id, organizationId, parentUnitId, name, code));
        return unit;
    }

    public void dissolve() {
        if (status == OrganizationUnitStatus.DISSOLVED) {
            return;
        }
        status = OrganizationUnitStatus.DISSOLVED;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new OrganizationUnitDissolved(getId(), organizationId));
    }

    public void rename(String newName) {
        Objects.requireNonNull(newName, "newName must not be null");
        if (newName.isBlank()) {
            throw new IllegalArgumentException("Unit name must not be blank");
        }
        this.name = newName;
        incrementVersion();
        updatedAt = Instant.now();
    }

    public OrganizationId getOrganizationId() { return organizationId; }
    public OrganizationUnitId getParentUnitId() { return parentUnitId; }
    public OrganizationUnitKind getKind() { return kind; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public OrganizationUnitStatus getStatus() { return status; }
    public boolean isActive() { return status == OrganizationUnitStatus.ACTIVE; }
    public boolean isRoot() { return parentUnitId == null; }
}
