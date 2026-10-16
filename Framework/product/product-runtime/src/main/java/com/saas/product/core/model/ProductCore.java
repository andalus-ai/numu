/**
 * @deprecated This is the pre-1.0 duplicate model (com.saas.product.*).
 *     It is NOT the canonical Product model. The canonical model lives
 *     in tech.kayys.numou.product.domain.* (numou-product-domain).
 *     Do not copy semantics from here; migrate to the Product 1.0 model.
 *     This module is excluded from the Maven reactor and must be deleted
 *     once migration is complete.
 */
package com.saas.product.core.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable core identity of a product.
 * Domain-agnostic: contains only fields universal across ALL contexts
 * (e-commerce, FnB, subscription, POS, healthcare, etc.).
 *
 * Domain-specific data lives in ProductExtension implementations.
 */
public final class ProductCore {

    private final ProductId id;
    private final String tenantId;
    private final String sku;
    private final String name;
    private final String description;
    private final ProductType type;
    private final String categoryId;
    private final String brandId;
    private final Map<String, String> attributes;   // arbitrary key-value, e.g. color=red
    private final Map<String, String> labels;       // arbitrary labels, e.g. env=prod
    private final String externalRef;               // ERP / 3rd-party system ID

    private ProductCore(Builder builder) {
        this.id          = Objects.requireNonNull(builder.id, "id");
        this.tenantId    = Objects.requireNonNull(builder.tenantId, "tenantId");
        this.sku         = Objects.requireNonNull(builder.sku, "sku");
        this.name        = Objects.requireNonNull(builder.name, "name");
        this.description = builder.description;
        this.type        = Objects.requireNonNull(builder.type, "type");
        this.categoryId  = builder.categoryId;
        this.brandId     = builder.brandId;
        this.attributes  = Collections.unmodifiableMap(new HashMap<>(builder.attributes));
        this.labels      = Collections.unmodifiableMap(new HashMap<>(builder.labels));
        this.externalRef = builder.externalRef;
    }

    public ProductCore withName(String newName) {
        return toBuilder().name(newName).build();
    }

    public ProductCore withDescription(String desc) {
        return toBuilder().description(desc).build();
    }

    public ProductCore withAttribute(String key, String value) {
        Map<String, String> updated = new HashMap<>(attributes);
        updated.put(key, value);
        return toBuilder().attributes(updated).build();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id).tenantId(tenantId).sku(sku).name(name)
                .description(description).type(type).categoryId(categoryId)
                .brandId(brandId).attributes(attributes).labels(labels)
                .externalRef(externalRef);
    }

    // ---- Getters ----

    public ProductId getId()              { return id; }
    public String getTenantId()           { return tenantId; }
    public String getSku()                { return sku; }
    public String getName()               { return name; }
    public String getDescription()        { return description; }
    public ProductType getType()          { return type; }
    public String getCategoryId()         { return categoryId; }
    public String getBrandId()            { return brandId; }
    public Map<String, String> getAttributes() { return attributes; }
    public Map<String, String> getLabels() { return labels; }
    public String getExternalRef()        { return externalRef; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductCore that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ProductCore{id=" + id + ", sku=" + sku + ", name=" + name + "}";
    }

    // ---- Builder ----

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ProductId id;
        private String tenantId;
        private String sku;
        private String name;
        private String description;
        private ProductType type;
        private String categoryId;
        private String brandId;
        private Map<String, String> attributes = new HashMap<>();
        private Map<String, String> labels     = new HashMap<>();
        private String externalRef;

        public Builder id(ProductId id)                   { this.id = id; return this; }
        public Builder tenantId(String t)                 { this.tenantId = t; return this; }
        public Builder sku(String sku)                    { this.sku = sku; return this; }
        public Builder name(String name)                  { this.name = name; return this; }
        public Builder description(String d)              { this.description = d; return this; }
        public Builder type(ProductType type)             { this.type = type; return this; }
        public Builder categoryId(String c)               { this.categoryId = c; return this; }
        public Builder brandId(String b)                  { this.brandId = b; return this; }
        public Builder attributes(Map<String, String> a)  { this.attributes = a; return this; }
        public Builder labels(Map<String, String> l)      { this.labels = l; return this; }
        public Builder externalRef(String r)              { this.externalRef = r; return this; }

        public Builder attribute(String key, String value) {
            this.attributes.put(key, value);
            return this;
        }

        public ProductCore build() {
            if (id == null) id = ProductId.generate();
            return new ProductCore(this);
        }
    }
}
