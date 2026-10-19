package com.saas.product.service;

import com.saas.product.core.ProductAggregate;
import com.saas.product.core.model.ProductCore;
import com.saas.product.core.model.ProductId;
import com.saas.product.core.pricing.PricingContext;
import com.saas.product.core.pricing.PricingResult;
import com.saas.product.events.EventPublisher;
import com.saas.product.events.ProductEvent;
import com.saas.product.runtime.ProductBehaviorRegistry;
import com.saas.product.spi.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Application service — the single authoritative entry point for all product operations.
 *
 * Responsibilities:
 *  1. Load/save aggregates via ProductRepository
 *  2. Orchestrate domain logic (lifecycle, extensions, pricing)
 *  3. Run pluggable validators
 *  4. Publish domain events
 *  5. Enforce tenant isolation on every operation
 *
 * Does NOT contain business rules — those belong in ProductAggregate.
 * Does NOT contain query/search — that belongs in ProductQueryService.
 */
@ApplicationScoped
public class ProductService {

    private static final Logger LOG = Logger.getLogger(ProductService.class);

    @Inject ProductRepository repository;
    @Inject ProductBehaviorRegistry behaviorRegistry;
    @Inject EventPublisher eventPublisher;
    @Inject Instance<ProductValidator> validators;

    // ──────────────────────────────────────────────────────────────────────
    //  Create
    // ──────────────────────────────────────────────────────────────────────

    @Transactional
    public ProductAggregate create(ProductCore core, String actor) {
        ensureSkuUnique(core.getSku(), core.getTenantId());

        ProductAggregate product = ProductAggregate.create(core, actor);
        validate(product);
        repository.save(product);

        eventPublisher.publish(new ProductEvent.ProductCreated(
                product.getId(), product.getTenantId(),
                core.getSku(), core.getName(), core.getType(),
                actor, Instant.now()));

        LOG.infof("Product created: id=%s sku=%s tenant=%s",
                product.getId(), core.getSku(), core.getTenantId());
        return product;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Core updates
    // ──────────────────────────────────────────────────────────────────────

    @Transactional
    public ProductAggregate updateCore(ProductId id, String tenantId, ProductCore updated, String actor) {
        ProductAggregate product = load(id, tenantId);
        product.updateCore(updated, actor);
        validate(product);
        repository.save(product);

        eventPublisher.publish(new ProductEvent.ProductCoreUpdated(
                id, tenantId, "core", null, null, actor, Instant.now()));
        return product;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Lifecycle
    // ──────────────────────────────────────────────────────────────────────

    @Transactional
    public ProductAggregate activate(ProductId id, String tenantId, String actor) {
        ProductAggregate product = load(id, tenantId);
        validate(product);  // final validation before going live
        product.activate(actor);
        repository.save(product);
        eventPublisher.publish(new ProductEvent.ProductActivated(id, tenantId, actor, Instant.now()));
        return product;
    }

    @Transactional
    public ProductAggregate suspend(ProductId id, String tenantId, String reason, String actor) {
        ProductAggregate product = load(id, tenantId);
        product.suspend(actor);
        repository.save(product);
        eventPublisher.publish(new ProductEvent.ProductSuspended(id, tenantId, reason, actor, Instant.now()));
        return product;
    }

    @Transactional
    public ProductAggregate archive(ProductId id, String tenantId, String actor) {
        ProductAggregate product = load(id, tenantId);
        product.archive(actor);
        repository.save(product);
        eventPublisher.publish(new ProductEvent.ProductArchived(id, tenantId, actor, Instant.now()));
        return product;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Extensions
    // ──────────────────────────────────────────────────────────────────────

    @Transactional
    public ProductAggregate putExtension(ProductId id, String tenantId,
                                         ProductExtension extension, String actor) {
        ProductAggregate product = load(id, tenantId);
        product.putExtension(extension, actor);
        validate(product);
        repository.save(product);

        eventPublisher.publish(new ProductEvent.ExtensionAdded(
                id, tenantId, extension.getContext(), actor, Instant.now()));
        return product;
    }

    @Transactional
    public ProductAggregate removeExtension(ProductId id, String tenantId,
                                            String context, String actor) {
        ProductAggregate product = load(id, tenantId);
        product.removeExtension(context, actor);
        repository.save(product);

        eventPublisher.publish(new ProductEvent.ExtensionRemoved(
                id, tenantId, context, actor, Instant.now()));
        return product;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Pricing
    // ──────────────────────────────────────────────────────────────────────

    public PricingResult calculatePrice(ProductId id, String context, PricingContext pricingCtx) {
        ProductAggregate product = load(id, pricingCtx.getTenantId());

        var behavior = behaviorRegistry.get(context);
        behavior.assertOrderable(product);

        PricingResult result = behavior.calculatePrice(product, pricingCtx);

        eventPublisher.publish(new ProductEvent.PriceCalculated(
                id, pricingCtx.getTenantId(), context,
                pricingCtx.getChannelId(), pricingCtx.getCustomerId(),
                result.getFinalTotalPrice().getAmount().toPlainString(),
                result.getCurrencyCode(),
                pricingCtx.getQuantity(),
                pricingCtx.getCustomerId() != null ? pricingCtx.getCustomerId() : "anonymous",
                Instant.now()));

        return result;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Reads (thin wrappers — prefer injecting ProductQuery directly)
    // ──────────────────────────────────────────────────────────────────────

    public Optional<ProductAggregate> findById(ProductId id, String tenantId) {
        return repository.findById(id, tenantId);
    }

    public Optional<ProductAggregate> findBySku(String sku, String tenantId) {
        return repository.findBySku(sku, tenantId);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ──────────────────────────────────────────────────────────────────────

    private ProductAggregate load(ProductId id, String tenantId) {
        return repository.findById(id, tenantId)
                .orElseThrow(() -> new ProductNotFoundException(id, tenantId));
    }

    private void ensureSkuUnique(String sku, String tenantId) {
        if (repository.findBySku(sku, tenantId).isPresent()) {
            throw new DuplicateSkuException(sku, tenantId);
        }
    }

    private void validate(ProductAggregate product) {
        List<String> allViolations = new ArrayList<>();
        for (ProductValidator v : validators) {
            if (product.hasExtension(v.supports())) {
                allViolations.addAll(v.collectViolations(product));
            }
        }
        if (!allViolations.isEmpty()) {
            throw new ProductValidationException(allViolations);
        }
    }
}
