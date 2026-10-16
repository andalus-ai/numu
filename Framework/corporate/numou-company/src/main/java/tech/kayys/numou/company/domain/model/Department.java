package tech.kayys.syirkah.company.domain.model;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.company.domain.identifier.CompanyId;
import tech.kayys.syirkah.company.domain.identifier.DepartmentId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Department aggregate root.
 * Represents an organizational department within a company.
 */
public final class Department extends AbstractAggregateRoot<DepartmentId> {
    
    private static final long serialVersionUID = 1L;
    
    private String name;
    private String code;
    private String description;
    private CompanyId companyId;
    private DepartmentId parentDepartmentId;
    private List<DepartmentId> childDepartmentIds;
    private String managerUserId;
    private String costCenter;
    private String location;
    private boolean active;

    private Department(DepartmentId id) {
        super(id);
        this.childDepartmentIds = new ArrayList<>();
        this.active = true;
    }

    private Department() {
        super();
    }

    /**
     * Factory method to create a new department.
     */
    public static Department create(
            DepartmentId id,
            String name,
            String code,
            CompanyId companyId) {
        Department department = new Department(id);
        department.name = name;
        department.code = code;
        department.companyId = companyId;
        return department;
    }

    /**
     * Adds a child department.
     */
    public void addChildDepartment(DepartmentId childDepartmentId) {
        if (!childDepartmentIds.contains(childDepartmentId)) {
            childDepartmentIds.add(childDepartmentId);
            setUpdatedAt(Instant.now());
            incrementVersion();
        }
    }

    /**
     * Removes a child department.
     */
    public void removeChildDepartment(DepartmentId childDepartmentId) {
        childDepartmentIds.remove(childDepartmentId);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets the parent department.
     */
    public void setParentDepartment(DepartmentId parentDepartmentId) {
        if (this.id.equals(parentDepartmentId)) {
            throw new IllegalArgumentException("Cannot set self as parent");
        }
        this.parentDepartmentId = parentDepartmentId;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Deactivates the department.
     */
    public void deactivate() {
        this.active = false;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Activates the department.
     */
    public void activate() {
        this.active = true;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    // Getters and Setters
    public String getName() { return name; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public CompanyId getCompanyId() { return companyId; }
    public DepartmentId getParentDepartmentId() { return parentDepartmentId; }
    public List<DepartmentId> getChildDepartmentIds() { return Collections.unmodifiableList(childDepartmentIds); }
    public String getManagerUserId() { return managerUserId; }
    public String getCostCenter() { return costCenter; }
    public String getLocation() { return location; }
    public boolean isActive() { return active; }

    public void setDescription(String description) {
        this.description = description;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setManagerUserId(String managerUserId) {
        this.managerUserId = managerUserId;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setCostCenter(String costCenter) {
        this.costCenter = costCenter;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setLocation(String location) {
        this.location = location;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "Department{" +
                "id=" + getId() +
                ", name='" + name + '\'' +
                ", code='" + code + '\'' +
                ", active=" + active +
                '}';
    }
}
