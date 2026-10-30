package tech.kayys.numou.crm.infrastructure.persistence.entity;

import tech.kayys.numou.foundation.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Territory entity for persistence.
 */
@Entity
@Table(name = "crm_territories", indexes = {
    @Index(name = "idx_territory_parent", columnList = "parent_territory_id"),
    @Index(name = "idx_territory_active", columnList = "territory_active"),
    @Index(name = "idx_territory_tenant", columnList = "tenant_id"),
    @Index(name = "idx_territory_tenant_parent", columnList = "tenant_id, parent_territory_id")
})
public class TerritoryEntity extends BaseEntity {

    @Column(name = "tenant_id")
    public UUID tenantId;

    @Column(name = "name", nullable = false, length = 200)
    public String name;

    @Column(name = "description", length = 2000)
    public String description;

    @Column(name = "parent_territory_id")
    public UUID parentTerritoryId;

    /** Territory lifecycle flag; distinct from {@link BaseEntity#active} soft-delete. */
    @Column(name = "territory_active", nullable = false)
    public boolean territoryActive;
}
