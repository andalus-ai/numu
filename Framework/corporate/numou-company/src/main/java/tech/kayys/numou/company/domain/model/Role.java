package tech.kayys.syirkah.company.domain.model;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.company.domain.identifier.RoleId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Role aggregate root.
 * Represents a role with associated permissions for RBAC.
 */
public final class Role extends AbstractAggregateRoot<RoleId> {
    
    private static final long serialVersionUID = 1L;
    
    private String name;
    private String description;
    private List<String> permissions;
    private boolean active;
    private boolean systemRole;

    private Role(RoleId id) {
        super(id);
        this.permissions = new ArrayList<>();
        this.active = true;
        this.systemRole = false;
    }

    private Role() {
        super();
    }

    /**
     * Factory method to create a new role.
     */
    public static Role create(RoleId id, String name, String description) {
        Role role = new Role(id);
        role.name = name;
        role.description = description;
        return role;
    }

    /**
     * Adds a permission to the role.
     */
    public void addPermission(String permission) {
        if (!permissions.contains(permission)) {
            permissions.add(permission);
            setUpdatedAt(Instant.now());
            incrementVersion();
        }
    }

    /**
     * Adds multiple permissions to the role.
     */
    public void addPermissions(List<String> permissions) {
        for (String permission : permissions) {
            addPermission(permission);
        }
    }

    /**
     * Removes a permission from the role.
     */
    public void removePermission(String permission) {
        permissions.remove(permission);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Checks if the role has a specific permission.
     */
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    /**
     * Activates the role.
     */
    public void activate() {
        this.active = true;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Deactivates the role.
     */
    public void deactivate() {
        if (systemRole) {
            throw new IllegalStateException("Cannot deactivate system role");
        }
        this.active = false;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    // Getters and Setters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<String> getPermissions() { return Collections.unmodifiableList(permissions); }
    public boolean isActive() { return active; }
    public boolean isSystemRole() { return systemRole; }

    public void setDescription(String description) {
        this.description = description;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setSystemRole(boolean systemRole) {
        if (systemRole) {
            this.systemRole = true;
        }
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "Role{" +
                "id=" + getId() +
                ", name='" + name + '\'' +
                ", permissions=" + permissions.size() +
                ", active=" + active +
                '}';
    }
}
