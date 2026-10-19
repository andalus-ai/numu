package com.saas.product.infrastructure.jpa;

import com.saas.product.core.ProductAggregate;
import com.saas.product.core.lifecycle.ProductStatus;
import com.saas.product.core.model.ProductId;
import com.saas.product.spi.ProductQuery;
import com.saas.product.spi.ProductRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of both ProductRepository and ProductQuery.
 *
 * Use this in unit tests that need a real repository without CDI or a DB.
 *
 * Example:
 *   var repo = new InMemoryProductRepository();
 *   var service = new ProductService(repo, registry, publisher, List.of());
 */
public class InMemoryProductRepository implements ProductRepository, ProductQuery {

    // key: tenantId + "::" + productId
    private final Map<String, ProductAggregate> store = new ConcurrentHashMap<>();

    private String key(ProductId id, String tenantId) {
        return tenantId + "::" + id.getValue();
    }

    // ── ProductRepository ─────────────────────────────────────────────────

    @Override
    public ProductAggregate save(ProductAggregate product) {
        store.put(key(product.getId(), product.getTenantId()), product);
        return product;
    }

    @Override
    public Optional<ProductAggregate> findById(ProductId id, String tenantId) {
        return Optional.ofNullable(store.get(key(id, tenantId)));
    }

    @Override
    public Optional<ProductAggregate> findBySku(String sku, String tenantId) {
        return store.values().stream()
                .filter(p -> p.getTenantId().equals(tenantId)
                          && p.getCore().getSku().equals(sku))
                .findFirst();
    }

    @Override
    public void delete(ProductId id, String tenantId) {
        store.remove(key(id, tenantId));
    }

    @Override
    public boolean exists(ProductId id, String tenantId) {
        return store.containsKey(key(id, tenantId));
    }

    // ── ProductQuery ──────────────────────────────────────────────────────

    @Override
    public ProductPage findAll(String tenantId, int page, int size) {
        return page(tenantId, null, null, page, size);
    }

    @Override
    public ProductPage findByStatus(String tenantId, ProductStatus status, int page, int size) {
        return page(tenantId, status, null, page, size);
    }

    @Override
    public ProductPage findByCategory(String tenantId, String categoryId, int page, int size) {
        List<ProductAggregate> all = store.values().stream()
                .filter(p -> p.getTenantId().equals(tenantId)
                          && categoryId.equals(p.getCore().getCategoryId()))
                .sorted(Comparator.comparing(p -> p.getCore().getName()))
                .collect(Collectors.toList());
        return slice(all, page, size);
    }

    @Override
    public ProductPage findByContext(String tenantId, String context, int page, int size) {
        List<ProductAggregate> all = store.values().stream()
                .filter(p -> p.getTenantId().equals(tenantId) && p.hasExtension(context))
                .collect(Collectors.toList());
        return slice(all, page, size);
    }

    @Override
    public ProductPage search(String tenantId, String query, int page, int size) {
        String lq = query.toLowerCase();
        List<ProductAggregate> all = store.values().stream()
                .filter(p -> p.getTenantId().equals(tenantId))
                .filter(p -> p.getCore().getName().toLowerCase().contains(lq)
                          || p.getCore().getSku().toLowerCase().contains(lq)
                          || (p.getCore().getDescription() != null
                              && p.getCore().getDescription().toLowerCase().contains(lq)))
                .collect(Collectors.toList());
        return slice(all, page, size);
    }

    @Override
    public List<ProductAggregate> findBySkuPrefix(String tenantId, String skuPrefix, int limit) {
        return store.values().stream()
                .filter(p -> p.getTenantId().equals(tenantId)
                          && p.getCore().getSku().startsWith(skuPrefix))
                .sorted(Comparator.comparing(p -> p.getCore().getSku()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    // ── Test helpers ──────────────────────────────────────────────────────

    /** Clear all products (useful between tests) */
    public void clear() {
        store.clear();
    }

    /** Total number of stored products across all tenants */
    public int size() {
        return store.size();
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private ProductPage page(String tenantId, ProductStatus status,
                              String context, int page, int size) {
        List<ProductAggregate> all = store.values().stream()
                .filter(p -> p.getTenantId().equals(tenantId))
                .filter(p -> status == null || p.getStatus() == status)
                .filter(p -> context == null || p.hasExtension(context))
                .sorted(Comparator.comparing(ProductAggregate::getUpdatedAt).reversed())
                .collect(Collectors.toList());
        return slice(all, page, size);
    }

    private ProductPage slice(List<ProductAggregate> all, int page, int size) {
        int from  = Math.min(page * size, all.size());
        int to    = Math.min(from + size, all.size());
        return new ProductPage(all.subList(from, to), all.size(), page, size);
    }
}
