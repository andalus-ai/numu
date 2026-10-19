package com.saas.product.infrastructure.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * JPA entity for product persistence.
 *
 * Design decisions:
 *  - Core fields are first-class columns (queryable, indexable)
 *  - Extension data stored as JSONB (PostgreSQL) → schema-free, forward-compatible
 *  - attributes stored as JSONB (arbitrary key-value metadata)
 *  - version column enables optimistic locking
 *  - tenant_id on every table — mandatory for multi-tenancy
 *
 * DO NOT expose this class outside the infrastructure package.
 * Use ProductAggregate in all domain and service code.
 */
@Entity
@Table(
    name = "products",
    indexes = {
        @Index(name = "idx_product_tenant_sku",    columnList = "tenant_id, sku",    unique = true),
        @Index(name = "idx_product_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_product_tenant_cat",    columnList = "tenant_id, category_id"),
        @Index(name = "idx_product_tenant_type",   columnList = "tenant_id, type"),
        @Index(name = "idx_product_updated_at",    columnList = "updated_at")
    }
)
public class ProductJpaEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    public String id;

    @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
    public String tenantId;

    @Column(name = "sku", length = 128, nullable = false)
    public String sku;

    @Column(name = "name", length = 512, nullable = false)
    public String name;

    @Column(name = "description", columnDefinition = "TEXT")
    public String description;

    @Column(name = "type", length = 32, nullable = false)
    public String type;

    @Column(name = "category_id", length = 64)
    public String categoryId;

    @Column(name = "brand_id", length = 64)
    public String brandId;

    @Column(name = "external_ref", length = 128)
    public String externalRef;

    @Column(name = "status", length = 32, nullable = false)
    public String status;

    /**
     * JSONB column holding serialized extension data.
     * Structure: { "ecommerce": { ... }, "fnb": { ... } }
     *
     * Each extension is serialized/deserialized by ProductAggregateMapper.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extensions", columnDefinition = "jsonb")
    public Map<String, Object> extensions;

    /**
     * JSONB column for arbitrary product attributes.
     * Structure: { "color": "red", "material": "cotton" }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", columnDefinition = "jsonb")
    public Map<String, String> attributes;

    /**
     * JSONB column for arbitrary labels/tags.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "labels", columnDefinition = "jsonb")
    public Map<String, String> labels;

    @Version
    @Column(name = "version", nullable = false)
    public long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(name = "created_by", length = 128, updatable = false)
    public String createdBy;

    @Column(name = "updated_by", length = 128)
    public String updatedBy;
}
