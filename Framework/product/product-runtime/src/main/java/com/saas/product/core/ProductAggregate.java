/**
 * @deprecated This is the pre-1.0 duplicate model (com.saas.product.*).
 *     It is NOT the canonical Product model. The canonical model lives
 *     in tech.kayys.numou.product.domain.* (numou-product-domain).
 *     Do not copy semantics from here; migrate to the Product 1.0 model.
 *     This module is excluded from the Maven reactor and must be deleted
 *     once migration is complete.
 */
package com.saas.product.core;

import com.saas.product.core.lifecycle.ProductStatus;
import com.saas.product.core.model.ProductCore;
import com.saas.product.core.model.ProductId;
import com.saas.product.spi.ProductExtension;

import java.time.Instant;
import java.util.*;

/**
 * Product Aggregate Root.
 *
 * Invariants enforced here:
 *  - Lifecycle transitions must follow the allowed state machine
 *  - Core identity is immutable after construction (replaced via withCore)
 *  - Every mutation increments the version (optimistic locking support)
 *  - Extensions are keyed by context name (e.g. "ecommerce", "fnb", "subscription")
 *
 * This class intentionally has NO framework annotations — it is framework-agnostic.
 * Infrastructure layers wrap/map it to JPA entities, Panache entities, or documents.
 */
public class ProductAggregate {

    private ProductCore core;
    private Map<String, ProductExtension> extensions;
    private ProductStatus status;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;

    // ---- Construction ----

    /**
     * Create a new product in DRAFT state.
     */
    public static ProductAggregate create(ProductCore core, String createdBy) {
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(createdBy, "createdBy");
        ProductAggregate p = new ProductAggregate();
        p.core       = core;
        p.extensions = new LinkedHashMap<>();
        p.status     = ProductStatus.DRAFT;
        p.version    = 1L;
        p.createdAt  = Instant.now();
        p.updatedAt  = p.createdAt;
        p.createdBy  = createdBy;
        p.updatedBy  = createdBy;
        return p;
    }

    /**
     * Reconstitute from persistence layer (no events raised, no state validation).
     */
    public static ProductAggregate reconstitute(
            ProductCore core,
            Map<String, ProductExtension> extensions,
            ProductStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt,
            String createdBy,
            String updatedBy) {

        ProductAggregate p = new ProductAggregate();
        p.core       = core;
        p.extensions = new LinkedHashMap<>(extensions);
        p.status     = status;
        p.version    = version;
        p.createdAt  = createdAt;
        p.updatedAt  = updatedAt;
        p.createdBy  = createdBy;
        p.updatedBy  = updatedBy;
        return p;
    }

    private ProductAggregate() {}

    // ---- Lifecycle Commands ----

    public void activate(String actor) {
        transitionTo(ProductStatus.ACTIVE, actor);
    }

    public void suspend(String actor) {
        transitionTo(ProductStatus.SUSPENDED, actor);
    }

    public void archive(String actor) {
        transitionTo(ProductStatus.ARCHIVED, actor);
    }

    private void transitionTo(ProductStatus target, String actor) {
        if (!status.canTransitionTo(target)) {
            throw new ProductLifecycleException(
                    "Illegal transition: " + status + " → " + target + " (product=" + core.getId() + ")");
        }
        this.status = target;
        touch(actor);
    }

    // ---- Mutation Commands ----

    public void updateCore(ProductCore updated, String actor) {
        assertNotArchived();
        if (!updated.getId().equals(core.getId())) {
            throw new IllegalArgumentException("Cannot change product identity");
        }
        this.core = updated;
        touch(actor);
    }

    public void putExtension(ProductExtension extension, String actor) {
        assertNotArchived();
        Objects.requireNonNull(extension, "extension");
        extensions.put(extension.getContext(), extension);
        touch(actor);
    }

    public void removeExtension(String context, String actor) {
        assertNotArchived();
        extensions.remove(context);
        touch(actor);
    }

    // ---- Queries ----

    public boolean hasExtension(String context) {
        return extensions.containsKey(context);
    }

    @SuppressWarnings("unchecked")
    public <T extends ProductExtension> Optional<T> findExtension(String context) {
        return Optional.ofNullable((T) extensions.get(context));
    }

    @SuppressWarnings("unchecked")
    public <T extends ProductExtension> T requireExtension(String context) {
        T ext = (T) extensions.get(context);
        if (ext == null) {
            throw new IllegalStateException(
                    "Extension '" + context + "' not configured on product " + core.getId());
        }
        return ext;
    }

    public boolean isOrderable() {
        return status == ProductStatus.ACTIVE;
    }

    public boolean isEditable() {
        return status != ProductStatus.ARCHIVED;
    }

    // ---- Internals ----

    private void assertNotArchived() {
        if (status == ProductStatus.ARCHIVED) {
            throw new ProductLifecycleException(
                    "Cannot modify archived product: " + core.getId());
        }
    }

    private void touch(String actor) {
        this.updatedAt = Instant.now();
        this.updatedBy = actor;
        this.version++;
    }

    // ---- Getters ----

    public ProductId getId()                           { return core.getId(); }
    public String getTenantId()                        { return core.getTenantId(); }
    public ProductCore getCore()                       { return core; }
    public Map<String, ProductExtension> getExtensions() { return Collections.unmodifiableMap(extensions); }
    public ProductStatus getStatus()                   { return status; }
    public long getVersion()                           { return version; }
    public Instant getCreatedAt()                      { return createdAt; }
    public Instant getUpdatedAt()                      { return updatedAt; }
    public String getCreatedBy()                       { return createdBy; }
    public String getUpdatedBy()                       { return updatedBy; }
}
